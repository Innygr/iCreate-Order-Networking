package com.innygr.icreateordernetworking.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.innygr.icreateordernetworking.logistics.FactorySourcingPlan;
import com.innygr.icreateordernetworking.logistics.StockLinkBindingData;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Bridges an authorized factory Stock Link sourcing plan into the existing
 * durable automated-delivery transaction. Allocations are revalidated after
 * the shipment journal is persisted, then physically collected at the source
 * Terminal Logger before the transport adapter packages them.
 */
public final class FactorySourcedDeliveryService {
    private FactorySourcedDeliveryService() {}

    public static DeliveryPreparation prepare(ServerLevel level, DeliveryRequest request,
                                               BlockPos rootStockLink, BlockPos requester) {
        if (level == null || request == null || rootStockLink == null || requester == null
                || request.route() != DeliveryRoute.FROGPORT
                || level.getServer() == null || !level.getServer().isSameThread()) {
            return DeliveryPreparation.failure(DeliveryPreparation.Status.INVALID_REQUEST,
                    "Factory-sourced dispatch requires a valid Frogport request and loaded server context.");
        }
        Item item = BuiltInRegistries.ITEM.get(request.itemId());
        if (item == null || BuiltInRegistries.ITEM.getKey(item).equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return DeliveryPreparation.failure(DeliveryPreparation.Status.ORDER_ITEM_MISMATCH,
                    "The requested order item is not registered.");
        }
        FactorySourcingPlan plan = FactorySourcingPlan.createForAuthorizedStockLink(
                level, rootStockLink, requester, item, request.count(), request.actorId());
        if (!plan.fullySourcedLocally() || plan.locallyAvailable() != request.count()) {
            return DeliveryPreparation.failure(DeliveryPreparation.Status.CARGO_UNAVAILABLE,
                    "Authorized factory stock cannot cover the complete shipment locally.");
        }
        if (!plan.revalidateForAuthorizedStockLink(level, rootStockLink, request.actorId(), item)) {
            return DeliveryPreparation.failure(DeliveryPreparation.Status.NOT_AUTHORIZED,
                    "Factory stock allocations failed their authorization or availability recheck.");
        }
        return AutomatedDeliveryService.prepare(level, request,
                (serverLevel, deliveryRequest) -> reserveAllocations(
                        serverLevel, deliveryRequest, rootStockLink, item, plan));
    }

    private static boolean reserveAllocations(ServerLevel level, DeliveryRequest request,
                                               BlockPos rootStockLink, Item item,
                                               FactorySourcingPlan plan) {
        if (!plan.fullySourcedLocally()
                || !plan.revalidateForAuthorizedStockLink(level, rootStockLink, request.actorId(), item)) {
            return false;
        }
        String[] endpoints = request.sourceReference().split(">", -1);
        if (endpoints.length != 2) return false;
        BlockPos sourcePos = parsePos(endpoints[0]);
        if (sourcePos == null || !level.hasChunkAt(sourcePos)
                || !(level.getBlockEntity(sourcePos) instanceof TerminalLoggerBlockEntity logger)
                || !request.actorId().equals(logger.getOwnerId())
                || !logger.acceptsItem(request.itemId())) return false;

        List<SourceSlice> slices = new ArrayList<>();
        ItemStack template = null;
        int sourceAvailable = 0;
        Set<BlockPos> seenPackagers = new HashSet<>();
        for (FactorySourcingPlan.Allocation allocation : plan.allocations()) {
            BlockPos linkPos = allocation.source().position();
            if (!level.hasChunkAt(linkPos)
                    || StockLinkBindingData.get(level).authorizedBinding(level, linkPos, request.actorId()) == null
                    || !request.itemId().equals(allocation.source().itemId())) return false;
            PackagerLinkBlockEntity link = level.getBlockEntity(linkPos) instanceof PackagerLinkBlockEntity found
                    ? found : null;
            PackagerBlockEntity packager = link == null ? null : link.getPackager();
            IItemHandler inventory = packager == null || packager.targetInventory == null
                    ? null : packager.targetInventory.getInventory();
            if (inventory == null || !seenPackagers.add(packager.getBlockPos().immutable())) return false;

            int needed = allocation.count();
            for (int slot = 0; slot < inventory.getSlots() && needed > 0; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.itemId())
                        || TrainShipmentMarker.transactionId(stack) != null) continue;
                if (template == null) template = stack.copyWithCount(1);
                if (!ItemStack.isSameItemSameComponents(template, stack)) continue;
                int amount = Math.min(needed, stack.getCount());
                slices.add(new SourceSlice(linkPos, inventory, slot, amount));
                needed -= amount;
                sourceAvailable += amount;
            }
            if (needed != 0) return false;
        }
        if (sourceAvailable != request.count() || template == null) return false;

        int stackSize = Math.max(1, template.getMaxStackSize());
        int requiredSlots = (request.count() + stackSize - 1) / stackSize;
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            if (logger.getInventory().getStackInSlot(slot).isEmpty()) emptySlots.add(slot);
        }
        if (emptySlots.size() < requiredSlots) return false;

        List<ItemStack> extracted = new ArrayList<>();
        try {
            for (SourceSlice slice : slices) {
                ItemStack stack = slice.inventory().extractItem(slice.slot(), slice.count(), false);
                extracted.add(stack);
                if (stack.isEmpty() || stack.getCount() != slice.count()
                        || !ItemStack.isSameItemSameComponents(template, stack)) {
                    restore(level, slices, extracted);
                    return false;
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            restore(level, slices, extracted);
            throw exception;
        }

        int stackIndex = 0;
        ItemStack bucket = ItemStack.EMPTY;
        for (ItemStack stack : extracted) {
            int remaining = stack.getCount();
            while (remaining > 0) {
                int amount = Math.min(remaining, stackSize);
                ItemStack part = stack.copyWithCount(amount);
                if (bucket.isEmpty()) bucket = part;
                else {
                    // Identical item/components were checked above; keep separate
                    // stacks so each inventory slot respects its normal stack limit.
                    TrainShipmentMarker.mark(bucket, request);
                    logger.getInventory().setStackInSlot(emptySlots.get(stackIndex++), bucket);
                    bucket = part;
                }
                remaining -= amount;
            }
        }
        if (!bucket.isEmpty()) {
            TrainShipmentMarker.mark(bucket, request);
            logger.getInventory().setStackInSlot(emptySlots.get(stackIndex), bucket);
        }
        logger.setChanged();
        return true;
    }

    private static void restore(ServerLevel level, List<SourceSlice> slices, List<ItemStack> extracted) {
        int index = 0;
        for (SourceSlice slice : slices) {
            if (index >= extracted.size()) break;
            ItemStack stack = extracted.get(index++);
            if (stack.isEmpty()) continue;
            ItemStack remainder = slice.inventory().insertItem(slice.slot(), stack, false);
            if (!remainder.isEmpty()) {
                // The source inventory changed unexpectedly. Preserve the cargo in
                // the source dimension rather than deleting it.
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, slice.position().getX() + 0.5, slice.position().getY() + 0.5,
                        slice.position().getZ() + 0.5, remainder));
            }
        }
    }

    private static BlockPos parsePos(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record SourceSlice(BlockPos position, IItemHandler inventory, int slot, int count) {}
}
