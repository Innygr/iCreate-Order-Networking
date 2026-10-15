package com.innygr.icreateordernetworking.delivery;

import java.util.UUID;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Server-owned transaction marker carried on a single stack through Create's
 * Portable Storage Interface. A marker is not a receipt until the destination
 * logger contains the complete marked stack and the route adapter verifies it.
 */
public final class TrainShipmentMarker {
    private static final String ROOT = "icreate_order_networking_train_shipment";
    private static final String TX = "Transaction";
    private static final String ORDER = "Order";
    private static final String CLAIMANT = "Claimant";
    private static final String ITEM = "Item";
    private static final String COUNT = "Count";
    private static final String SOURCE = "Source";
    private static final String DESTINATION = "Destination";

    private TrainShipmentMarker() {}

    public static void mark(ItemStack stack, DeliveryRequest request) {
        CompoundTag tag = customTag(stack);
        CompoundTag marker = new CompoundTag();
        marker.putString(TX, request.transactionId().toString());
        marker.putString(ORDER, request.orderId().toString());
        marker.putString(CLAIMANT, request.actorId().toString());
        marker.putString(ITEM, request.itemId().toString());
        marker.putInt(COUNT, request.count());
        marker.putString(SOURCE, request.sourceReference().substring(0, request.sourceReference().indexOf('>')));
        marker.putString(DESTINATION, request.sourceReference().substring(request.sourceReference().indexOf('>') + 1));
        tag.put(ROOT, marker);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static UUID transactionId(ItemStack stack) {
        CompoundTag marker = marker(stack);
        if (marker == null) return null;
        try { return UUID.fromString(marker.getString(TX)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static boolean matches(ItemStack stack, ShipmentJournalData.Shipment shipment) {
        CompoundTag marker = marker(stack);
        if (marker == null || shipment == null) return false;
        try {
            return shipment.transactionId().equals(UUID.fromString(marker.getString(TX)))
                    && shipment.orderId().equals(UUID.fromString(marker.getString(ORDER)))
                    && shipment.claimantId().equals(UUID.fromString(marker.getString(CLAIMANT)))
                    && shipment.route() == DeliveryRoute.TRAIN
                    && shipment.count() == marker.getInt(COUNT)
                    && shipment.sourceReference().equals(marker.getString(SOURCE) + ">" + marker.getString(DESTINATION))
                    && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.parse(marker.getString(ITEM)));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean matches(ItemStack stack, UUID transactionId) {
        return transactionId != null && transactionId.equals(transactionId(stack));
    }

    public static int countIn(TerminalLoggerBlockEntity logger, UUID transactionId) {
        if (logger == null || transactionId == null) return 0;
        int count = 0;
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            ItemStack stack = logger.getInventory().getStackInSlot(slot);
            if (matches(stack, transactionId)) count += stack.getCount();
        }
        return count;
    }

    /** Clears only this mod's marker keys and preserves any other custom item data. */
    public static void clear(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        if (!tag.contains(ROOT, Tag.TAG_COMPOUND)) return;
        tag.remove(ROOT);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearTransaction(TerminalLoggerBlockEntity logger, UUID transactionId) {
        if (logger == null || transactionId == null) return;
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            ItemStack stack = logger.getInventory().getStackInSlot(slot);
            if (!matches(stack, transactionId)) continue;
            ItemStack clean = stack.copy();
            clear(clean);
            logger.getInventory().setStackInSlot(slot, clean);
        }
    }

    /**
     * Called only after an item handler has accepted part of a marked stack.
     * Completion is attempted once the destination has the full transaction count.
     */
    public static void tryConfirmAtDestination(ServerLevel level, TerminalLoggerBlockEntity logger) {
        if (level == null || logger == null) return;
        String destination = EndpointReference.format(logger.getBlockPos());
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            ItemStack stack = logger.getInventory().getStackInSlot(slot);
            UUID transactionId = transactionId(stack);
            if (transactionId == null) continue;
            ShipmentJournalData journal = ShipmentJournalData.get(level);
            ShipmentJournalData.Shipment shipment = journal.find(transactionId);
            if (shipment == null || countIn(logger, transactionId) < shipment.count()
                    || !shipment.sourceReference().endsWith(">" + destination)) continue;

            boolean finalized = switch (shipment.stage()) {
                case DISPATCHED -> DurableShipmentService.confirmDestinationReceipt(
                        level, transactionId, destination);
                case DESTINATION_RECEIVED -> DurableShipmentService.retryPayout(level, transactionId);
                case COMPLETED -> destination.equals(shipment.destinationReference());
                case RESERVED -> false;
            };
            if (finalized) clearTransaction(logger, transactionId);
        }
    }

    private static final class EndpointReference {
        private static String format(net.minecraft.core.BlockPos pos) {
            return pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
    }

    private static CompoundTag marker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag root = customTag(stack);
        return root.contains(ROOT, CompoundTag.TAG_COMPOUND) ? root.getCompound(ROOT) : null;
    }

    private static CompoundTag customTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }
}
