package com.innygr.icreateordernetworking.delivery;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-thread protocol for durable shipment milestones. Route adapters must
 * verify dispatch and destination receipt independently before advancing stages.
 */
public final class DurableShipmentService {
    private DurableShipmentService() {}

    public static boolean recordReserved(ServerLevel level, DeliveryRequest request) {
        if (!onServerThread(level) || request == null) return false;
        // An order receipt is the authoritative quantity reservation; this journal
        // adds dispatch/recovery milestones for the same transaction ID.
        var receipt = com.innygr.icreateordernetworking.orders.OrderData.get(level)
                .deliveryReceipt(request.transactionId());
        if (receipt == null || !receipt.matches(request.orderId(), request.route(), request.actorId(), request.count())) {
            return false;
        }
        return ShipmentJournalData.get(level).reserve(request);
    }

    public static boolean markDispatched(ServerLevel level, UUID transactionId) {
        return onServerThread(level) && ShipmentJournalData.get(level)
                .advance(transactionId, ShipmentJournalData.Stage.RESERVED, ShipmentJournalData.Stage.DISPATCHED, null);
    }

    /** Call only after the destination inventory/endpoint verifies the matching transaction and item count. */
    public static boolean confirmDestinationReceipt(ServerLevel level, UUID transactionId, String destinationReference) {
        if (!onServerThread(level)) return false;
        ShipmentJournalData journal = ShipmentJournalData.get(level);
        if (!journal.advance(transactionId, ShipmentJournalData.Stage.DISPATCHED,
                ShipmentJournalData.Stage.DESTINATION_RECEIVED, destinationReference)) return false;
        return finalizePayout(level, transactionId, journal);
    }

    /** Retry this after restart for any journal entry at DESTINATION_RECEIVED. */
    public static boolean retryPayout(ServerLevel level, UUID transactionId) {
        if (!onServerThread(level)) return false;
        ShipmentJournalData journal = ShipmentJournalData.get(level);
        ShipmentJournalData.Shipment shipment = journal.find(transactionId);
        if (shipment == null || shipment.stage() != ShipmentJournalData.Stage.DESTINATION_RECEIVED) return false;
        return finalizePayout(level, transactionId, journal);
    }

    /**
     * Startup recovery pass. Destination receipt is already durable at this stage,
     * so payout can be retried safely without touching physical cargo. RESERVED and
     * DISPATCHED transactions deliberately remain pending for their transport adapter
     * to reconcile; this service must never invent a receipt for them.
     *
     * @return number of transactions successfully finalized during this pass
     */
    public static int recoverPendingPayouts(ServerLevel level) {
        if (!onServerThread(level)) return 0;
        ShipmentJournalData journal = ShipmentJournalData.get(level);
        int recovered = 0;
        for (ShipmentJournalData.Shipment shipment : journal.pending().values()) {
            if (shipment.stage() == ShipmentJournalData.Stage.DESTINATION_RECEIVED
                    && retryPayout(level, shipment.transactionId())) {
                recovered++;
            }
        }
        return recovered;
    }

    private static boolean finalizePayout(ServerLevel level, UUID transactionId, ShipmentJournalData journal) {
        ShipmentJournalData.Shipment shipment = journal.find(transactionId);
        if (shipment == null || shipment.stage() != ShipmentJournalData.Stage.DESTINATION_RECEIVED) return false;
        if (!AutomatedDeliveryService.completeAfterDestinationReceipt(level, transactionId)) return false;
        return journal.advance(transactionId, ShipmentJournalData.Stage.DESTINATION_RECEIVED,
                ShipmentJournalData.Stage.COMPLETED, null);
    }

    private static boolean onServerThread(ServerLevel level) {
        return level != null && level.getServer() != null && level.getServer().isSameThread();
    }
}
