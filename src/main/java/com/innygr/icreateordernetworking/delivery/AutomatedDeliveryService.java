package com.innygr.icreateordernetworking.delivery;

import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.orders.OrderStatus;
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

        PreparedDelivery prepared;
        try {
            prepared = adapter.prepareCargo(level, request);
        } catch (RuntimeException | LinkageError exception) {
            prepared = null;
        }
        if (prepared == null) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.CARGO_UNAVAILABLE,
                    "The adapter could not reserve cargo safely.");
        }
        return DeliveryPreparation.prepared(prepared);
    }
}
