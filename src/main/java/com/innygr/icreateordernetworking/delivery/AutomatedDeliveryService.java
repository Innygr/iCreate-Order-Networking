package com.innygr.icreateordernetworking.delivery;

import com.innygr.icreateordernetworking.orders.Order;
import net.minecraft.core.BlockPos;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.orders.OrderStatus;
import com.innygr.icreateordernetworking.orders.DeliveryReceipt;
import com.innygr.icreateordernetworking.economy.EconomyService;
import com.innygr.icreateordernetworking.orders.DeliveryService;
import net.minecraft.server.level.ServerLevel;

/**
 * Common preparation entry point for automated transport routes.
 *
 * Preparation validates the live order and reserves cargo, but never commits
 * cargo, fulfils an order, or issues rewards. Those actions require a later
 * idempotent transport transaction and a verified destination receipt.
 */
public final class AutomatedDeliveryService {
    private AutomatedDeliveryService() {}

    public static DeliveryPreparation prepare(ServerLevel level, DeliveryRequest request) {
        if (level == null || request == null || !request.isWellFormed()) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.INVALID_REQUEST, "Delivery request is incomplete.");
        }
        if (level.getServer() == null || !level.getServer().isSameThread()) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.WRONG_THREAD, "Delivery preparation must run on the server thread.");
        }

        // Validate against authoritative order state before an adapter can reserve
        // any physical cargo. Request metadata alone is never sufficient authority.
        OrderData data = OrderData.get(level);
        // Never ask an adapter to reserve the same physical cargo twice. A retry
        // with an existing transaction must be reconciled against its durable receipt
        // by the transport, not treated as a fresh preparation attempt.
        DeliveryReceipt existingReceipt = data.deliveryReceipt(request.transactionId());
        if (existingReceipt != null) {
            boolean sameTransaction = existingReceipt.matches(request.orderId(), request.route(),
                    request.actorId(), request.count());
            return DeliveryPreparation.failure(
                    sameTransaction ? DeliveryPreparation.Status.ORDER_UNAVAILABLE
                            : DeliveryPreparation.Status.INVALID_REQUEST,
                    sameTransaction
                            ? "This transaction is already reserved; resume or reconcile its existing shipment."
                            : "The transaction ID is already bound to a different delivery.");
        }
        Order order = data.find(request.orderId());
        if (order == null || (order.status() != OrderStatus.OPEN
                && order.status() != OrderStatus.IN_PROGRESS)) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.ORDER_UNAVAILABLE, "The order is missing or no longer active.");
        }
        if (!request.actorId().equals(order.claimedBy())) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.ORDER_NOT_CLAIMED,
                    "The delivery actor must be the current claimant of the order.");
        }
        if (!order.itemId().equals(request.itemId())
                || DeliveryService.resolveItem(request.itemId()) == null) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.ORDER_ITEM_MISMATCH,
                    "The requested cargo does not match a registered item on the order.");
        }
        long unreserved = (long) order.remainingCount()
                - data.pendingDeliveryCountForOrder(order.id(), null);
        if (request.count() > unreserved) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.ORDER_QUANTITY_UNAVAILABLE,
                    "The requested quantity exceeds the unreserved order quantity.");
        }

        AutomatedDeliveryAdapter adapter = AutomatedDeliveryRegistry.find(request.route()).orElse(null);
        if (adapter == null) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.ROUTE_UNAVAILABLE,
                    "No verified adapter is registered for route " + request.route() + ".");
        }

        boolean authorized;
        try {
            authorized = adapter.isAuthorized(level, request);
        } catch (RuntimeException | LinkageError exception) {
            authorized = false;
        }
        if (!authorized) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.NOT_AUTHORIZED,
                    "The transport source is not authorized for this request.");
        }

        // Persist the order quantity reservation and shipment journal BEFORE
        // touching physical cargo. A crash can then leave a recoverable RESERVED
        // transaction, never transaction-marked cargo with no durable journal row.
        if (!data.reserveDelivery(request.transactionId(), request.orderId(), request.route(),
                request.actorId(), request.count())) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.ORDER_QUANTITY_UNAVAILABLE,
                    "The order quantity could not be reserved for this transaction.");
        }

        if (!DurableShipmentService.recordReserved(level, request)) {
            data.forgetDelivery(request.transactionId());
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.ORDER_QUANTITY_UNAVAILABLE,
                    "The shipment journal could not reserve this transaction.");
        }

        PreparedDelivery prepared;
        try {
            prepared = adapter.prepareCargo(level, request);
        } catch (RuntimeException | LinkageError exception) {
            prepared = null;
        }
        if (prepared == null) {
            // No physical reservation was returned, so it is safe to remove only
            // this still-RESERVED journal row and release the order quantity.
            ShipmentJournalData.get(level).abandonReservation(request.transactionId());
            data.forgetDelivery(request.transactionId());
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.CARGO_UNAVAILABLE,
                    "The adapter could not reserve cargo safely.");
        }

        return DeliveryPreparation.prepared(prepared);
    }


    /**
     * Reconciles persisted RESERVED transactions whose tagged cargo is still held
     * by this source logger. Safe to call periodically; the adapter and journal
     * use the transaction UUID to avoid duplicate dispatch.
     */
    public static int resumeReservedAtSource(ServerLevel level, BlockPos sourcePosition) {
        if (level == null || sourcePosition == null || level.getServer() == null
                || !level.getServer().isSameThread()) return 0;
        String prefix = sourcePosition.getX() + "," + sourcePosition.getY() + ","
                + sourcePosition.getZ() + ">";
        int resumed = 0;
        for (ShipmentJournalData.Shipment shipment : ShipmentJournalData.get(level).pending().values()) {
            if (shipment.stage() != ShipmentJournalData.Stage.RESERVED
                    || !shipment.sourceReference().startsWith(prefix)) continue;
            AutomatedDeliveryAdapter adapter = AutomatedDeliveryRegistry.find(shipment.route()).orElse(null);
            if (adapter == null) continue;
            PreparedDelivery prepared;
            try {
                prepared = adapter.resumeCargo(level, shipment);
            } catch (RuntimeException | LinkageError ignored) {
                prepared = null;
            }
            if (prepared != null && DurableShipmentService.commitDispatch(
                    level, shipment.transactionId(), prepared)) resumed++;
        }
        return resumed;
    }

    /**
     * Finalizes an automated shipment after the transport independently verifies
     * destination receipt. The transaction ID must match the persisted reservation.
     * This method does not commit cargo; the route owns that step.
     */
    public static boolean completeAfterDestinationReceipt(ServerLevel level,
                                                           java.util.UUID transactionId) {
        if (level == null || transactionId == null || level.getServer() == null
                || !level.getServer().isSameThread()) return false;

        OrderData data = OrderData.get(level);
        DeliveryReceipt receipt = data.deliveryReceipt(transactionId);
        if (receipt == null || receipt.route() == DeliveryRoute.MANUAL
                || receipt.count() < 1 || receipt.claimantId() == null) return false;
        if (receipt.progressApplied()) return true;

        Order order = data.find(receipt.orderId());
        if (order == null) return false;

        // Persist the progress marker alongside the order mutation. If payout is
        // interrupted, retries skip fulfilment and safely retry the idempotent credit.
        if (!data.applyAutomatedDeliveryProgress(transactionId)) return false;

        long reward = (long) receipt.count() * order.rewardPerItem();
        if (reward > 0) {
            if (!EconomyService.canCreditOnce(level, receipt.claimantId(), reward, transactionId)
                    || !EconomyService.creditOnce(level, receipt.claimantId(), reward, transactionId)) {
                return false;
            }
        }
        return data.markDeliveryProgressApplied(transactionId);
    }
}
