package com.innygr.icreateordernetworking.delivery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Durable transport journal. This records protocol milestones, not physical proof:
 * adapters may advance DESTINATION_RECEIVED only after verifying receipt server-side.
 */
public final class ShipmentJournalData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_shipments";
    private final Map<UUID, Shipment> shipments = new LinkedHashMap<>();

    public enum Stage { RESERVED, DISPATCHED, DESTINATION_RECEIVED, COMPLETED }
    public record Shipment(UUID transactionId, UUID orderId, DeliveryRoute route, UUID claimantId,
                           int count, String sourceReference, String destinationReference, Stage stage) {}

    public static ShipmentJournalData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShipmentJournalData::new, ShipmentJournalData::load), DATA_NAME);
    }

    public static ShipmentJournalData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipmentJournalData data = new ShipmentJournalData();
        ListTag list = tag.getList("Shipments", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag n = list.getCompound(i);
            try {
                UUID tx = UUID.fromString(n.getString("Transaction"));
                UUID order = UUID.fromString(n.getString("Order"));
                UUID claimant = UUID.fromString(n.getString("Claimant"));
                DeliveryRoute route = DeliveryRoute.valueOf(n.getString("Route"));
                Stage stage = Stage.valueOf(n.getString("Stage"));
                int count = n.getInt("Count");
                String source = n.getString("Source");
                String destination = n.getString("Destination");
                boolean receiptStage = stage == Stage.DESTINATION_RECEIVED || stage == Stage.COMPLETED;
                boolean destinationValid = destination.length() <= 256
                        && (receiptStage ? !destination.isBlank() : destination.isEmpty());
                if (route != DeliveryRoute.MANUAL && count > 0 && count <= 4096
                        && !source.isBlank() && source.length() <= 256
                        && destinationValid && !data.shipments.containsKey(tx)) {
                    data.shipments.put(tx, new Shipment(tx, order, route, claimant, count, source, destination, stage));
                }
            } catch (RuntimeException ignored) {
                // One malformed transaction must not prevent world loading.
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Shipment shipment : shipments.values()) {
            CompoundTag n = new CompoundTag();
            n.putString("Transaction", shipment.transactionId().toString());
            n.putString("Order", shipment.orderId().toString());
            n.putString("Route", shipment.route().name());
            n.putString("Claimant", shipment.claimantId().toString());
            n.putInt("Count", shipment.count());
            n.putString("Source", shipment.sourceReference());
            n.putString("Destination", shipment.destinationReference());
            n.putString("Stage", shipment.stage().name());
            list.add(n);
        }
        tag.put("Shipments", list);
        return tag;
    }

    public Shipment find(UUID transactionId) { return shipments.get(transactionId); }

    public boolean reserve(DeliveryRequest request) {
        if (request == null || !request.isWellFormed() || request.sourceReference().isBlank()
                || shipments.containsKey(request.transactionId())) return false;
        shipments.put(request.transactionId(), new Shipment(request.transactionId(), request.orderId(),
                request.route(), request.actorId(), request.count(), request.sourceReference(), "", Stage.RESERVED));
        setDirty();
        return true;
    }

    /** Monotonic, idempotent stage transition. Cannot skip a proof boundary. */
    public boolean advance(UUID transactionId, Stage expected, Stage next, String destinationReference) {
        if (transactionId == null || expected == null || next == null
                || next.ordinal() != expected.ordinal() + 1) return false;
        // Only the receipt transition may carry a destination. Reject stray values
        // instead of silently accepting a malformed or confused caller.
        if (next == Stage.DESTINATION_RECEIVED) {
            if (destinationReference == null || destinationReference.isBlank()
                    || destinationReference.length() > 256) return false;
        } else if (destinationReference != null) {
            return false;
        }

        Shipment current = shipments.get(transactionId);
        if (current == null) return false;
        if (current.stage() == next) {
            // A repeated transition is idempotent only when its receipt identity
            // is exactly the same; non-receipt transitions must carry no endpoint.
            return next != Stage.DESTINATION_RECEIVED
                    || current.destinationReference().equals(destinationReference);
        }
        if (current.stage() != expected) return false;
        String destination = next == Stage.DESTINATION_RECEIVED
                ? destinationReference : current.destinationReference();
        shipments.put(transactionId, new Shipment(current.transactionId(), current.orderId(), current.route(),
                current.claimantId(), current.count(), current.sourceReference(), destination, next));
        setDirty();
        return true;
    }

    /**
     * Removes a reservation only while no dispatch or receipt has been recorded.
     * Used to unwind preparation failures; later stages are never deletable here.
     */
    public boolean abandonReservation(UUID transactionId) {
        if (transactionId == null) return false;
        Shipment current = shipments.get(transactionId);
        if (current == null || current.stage() != Stage.RESERVED) return false;
        shipments.remove(transactionId);
        setDirty();
        return true;
    }

    public Map<UUID, Shipment> pending() {
        Map<UUID, Shipment> result = new LinkedHashMap<>();
        shipments.forEach((id, shipment) -> { if (shipment.stage() != Stage.COMPLETED) result.put(id, shipment); });
        return java.util.Collections.unmodifiableMap(result);
    }
}
