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

        // Persist exact authorized source links/packagers before the first extraction.
        List<ShipmentJournalData.FactoryAllocation> manifest = new ArrayList<>();
        for (FactorySourcingPlan.Allocation allocation : plan.allocations()) {
            PackagerLinkBlockEntity link = (PackagerLinkBlockEntity) level.getBlockEntity(allocation.source().position());
            PackagerBlockEntity packager = link.getPackager();
            manifest.add(new ShipmentJournalData.FactoryAllocation(
                    allocation.source().position(), packager.getBlockPos(), allocation.count()));
        }
        ShipmentJournalData journal = ShipmentJournalData.get(level);
        if (!journal.saveFactoryManifest(request.transactionId(), manifest)) return false;

        int stackSize = Math.max(1, template.getMaxStackSize());
        int requiredSlots = (request.count() + stackSize - 1) / stackSize;
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            if (logger.getInventory().getStackInSlot(slot).isEmpty()) emptySlots.add(slot);
        }
        if (emptySlots.size() < requiredSlots) return false;

        int outputSlot = 0;
        try {
            for (SourceSlice slice : slices) {
                ItemStack stack = slice.inventory().extractItem(slice.slot(), slice.count(), false);
                if (stack.isEmpty() || stack.getCount() != slice.count()
                        || !ItemStack.isSameItemSameComponents(template, stack)) {
                    if (!stack.isEmpty()) {
                        ItemStack remainder = slice.inventory().insertItem(slice.slot(), stack, false);
                        if (!remainder.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                                level, slice.position().getX() + 0.5, slice.position().getY() + 0.5,
                                slice.position().getZ() + 0.5, remainder));
                    }
                    if (outputSlot > 0) throw new IllegalStateException(
                            "Factory collection interrupted after a partial durable reservation");
                    return false;
                }
                TrainShipmentMarker.mark(stack, request);
                // Coalesce compatible transaction stacks first so the number of
                // source slots does not artificially exceed the logger's capacity.
                for (int slot = 0; slot < logger.getInventory().getSlots() && !stack.isEmpty(); slot++) {
                    ItemStack existing = logger.getInventory().getStackInSlot(slot);
                    if (existing.isEmpty() || !TrainShipmentMarker.matches(existing, request.transactionId())
                            || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
                    int room = existing.getMaxStackSize() - existing.getCount();
                    if (room <= 0) continue;
                    int moved = Math.min(room, stack.getCount());
                    ItemStack merged = existing.copy();
                    merged.grow(moved);
                    logger.getInventory().setStackInSlot(slot, merged);
                    stack.shrink(moved);
                }
                if (!stack.isEmpty()) {
                    int target = -1;
                    for (int slot : emptySlots) {
                        if (logger.getInventory().getStackInSlot(slot).isEmpty()) { target = slot; break; }
                    }
                    if (target < 0) {
                        ItemStack remainder = slice.inventory().insertItem(slice.slot(), stack, false);
                        if (!remainder.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                                level, slice.position().getX() + 0.5, slice.position().getY() + 0.5,
                                slice.position().getZ() + 0.5, remainder));
                        if (outputSlot > 0) throw new IllegalStateException(
                                "Factory logger filled during a partial durable reservation");
                        return false;
                    }
                    logger.getInventory().setStackInSlot(target, stack);
                }
                logger.setChanged();
                outputSlot++;
            }
        } catch (RuntimeException | LinkageError exception) {
            // The persisted manifest plus any already marked logger stacks are
            // sufficient for source-side recovery after the server restarts.
            throw exception;
        }
        logger.setChanged();
        return true;
    }

    /**
     * Recover an interrupted multi-link collection. Only a RESERVED transaction
     * with a persisted manifest is eligible; it tops up the logger to the exact
     * shipment count from the same authorized sources before Frogport can package it.
     */
    public static int resumeFactoryCollectionAtSource(ServerLevel level, TerminalLoggerBlockEntity logger) {
        if (level == null || logger == null || level.getServer() == null
                || !level.getServer().isSameThread()) return 0;
        BlockPos loggerPos = logger.getBlockPos();
        String prefix = loggerPos.getX() + "," + loggerPos.getY() + "," + loggerPos.getZ() + ">";
        int recovered = 0;
        ShipmentJournalData journal = ShipmentJournalData.get(level);
        for (ShipmentJournalData.Shipment shipment : journal.pending().values()) {
            if (shipment.stage() != ShipmentJournalData.Stage.RESERVED
                    || !shipment.sourceReference().startsWith(prefix)) continue;
            List<ShipmentJournalData.FactoryAllocation> manifest = journal.factoryManifest(shipment.transactionId());
            if (manifest.isEmpty()) continue;
            int present = TrainShipmentMarker.countMatchingIn(logger, shipment);
            // Once Create has made a transaction-marked package, it may already be
            // travelling even if the journal still says RESERVED. Never refill the
            // source logger from factory stock in that state: it could duplicate a
            // shipment that has physically left the source.
            boolean packageExists = PackagerFrogportAdapter.hasMarkedPackageAtSource(level, shipment);
            // With no marked cargo at the source, the physical state is ambiguous:
            // the package may be in transit, or a crash may have happened between
            // extraction and insertion. Fail closed rather than collecting fresh
            // stock and risking a duplicate shipment. Partial marked logger cargo
            // remains recoverable from the persisted manifest.
            if (!shouldResumeFactoryCollection(present, shipment.count(), packageExists)) continue;
            com.innygr.icreateordernetworking.orders.Order order =
                    com.innygr.icreateordernetworking.orders.OrderData.get(level).find(shipment.orderId());
            if (order == null) continue;
            Item item = BuiltInRegistries.ITEM.get(order.itemId());
            if (item == null) continue;
            ItemStack template = null;
            for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
                ItemStack stack = logger.getInventory().getStackInSlot(slot);
                if (TrainShipmentMarker.matches(stack, shipment)) { template = stack.copyWithCount(1); break; }
            }
            int missing = shipment.count() - present;
            DeliveryRequest request = new DeliveryRequest(shipment.orderId(), order.itemId(), shipment.count(),
                    shipment.route(), shipment.claimantId(), shipment.sourceReference(), shipment.transactionId());
            for (ShipmentJournalData.FactoryAllocation allocation : manifest) {
                if (missing == 0) break;
                BlockPos linkPos = allocation.stockLink();
                if (!level.hasChunkAt(linkPos)
                        || StockLinkBindingData.get(level).authorizedBinding(level, linkPos, shipment.claimantId()) == null
                        || !(level.getBlockEntity(linkPos) instanceof PackagerLinkBlockEntity link)) continue;
                PackagerBlockEntity packager = link.getPackager();
                if (packager == null || !allocation.packager().equals(packager.getBlockPos())
                        || packager.targetInventory == null || packager.targetInventory.getInventory() == null) continue;
                IItemHandler inventory = packager.targetInventory.getInventory();
                int sourceBudget = allocation.count();
                for (int slot = 0; slot < inventory.getSlots() && missing > 0 && sourceBudget > 0; slot++) {
                    ItemStack available = inventory.getStackInSlot(slot);
                    if (available.isEmpty() || !item.equals(available.getItem())
                            || TrainShipmentMarker.transactionId(available) != null
                            || (template != null && !ItemStack.isSameItemSameComponents(template, available))) continue;
                    int amount = Math.min(missing, Math.min(sourceBudget, available.getCount()));
                    ItemStack stack = inventory.extractItem(slot, amount, false);
                    if (stack.isEmpty() || stack.getCount() != amount
                            || (template != null && !ItemStack.isSameItemSameComponents(template, stack))) {
                        if (!stack.isEmpty()) inventory.insertItem(slot, stack, false);
                        continue;
                    }
                    TrainShipmentMarker.mark(stack, request);
                    int target = -1;
                    for (int out = 0; out < logger.getInventory().getSlots(); out++) {
                        if (logger.getInventory().getStackInSlot(out).isEmpty()) { target = out; break; }
                    }
                    if (target < 0) {
                        ItemStack remainder = inventory.insertItem(slot, stack, false);
                        if (!remainder.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                                level, linkPos.getX() + 0.5, linkPos.getY() + 0.5, linkPos.getZ() + 0.5, remainder));
                        return recovered;
                    }
                    logger.getInventory().setStackInSlot(target, stack);
                    logger.setChanged();
                    missing -= stack.getCount();
                    sourceBudget -= stack.getCount();
                }
            }
            if (missing == 0) recovered++;
        }
        return recovered;
    }

    /** Pure recovery guard: only partial, source-marked cargo with no existing package may be topped up. */
    static boolean shouldResumeFactoryCollection(int markedAtLogger, int shipmentCount,
                                                  boolean markedPackageExistsAtSource) {
        return shipmentCount > 0 && markedAtLogger > 0 && markedAtLogger < shipmentCount
                && !markedPackageExistsAtSource;
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
