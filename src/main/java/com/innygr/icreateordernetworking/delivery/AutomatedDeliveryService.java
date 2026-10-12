package com.innygr.icreateordernetworking.delivery;

import net.minecraft.server.level.ServerLevel;

/**
 * Common preparation entry point for automated transport routes.
 *
 * This method only validates the request and asks the selected adapter to
 * reserve cargo. It does not commit cargo, fulfil an order, or issue rewards.
 * Those actions require a later idempotent transport/receipt transaction.
 */
public final class AutomatedDeliveryService {
    private AutomatedDeliveryService() {}

    public static DeliveryPreparation prepare(ServerLevel level, DeliveryRequest request) {
        if (level == null || request == null || !request.isWellFormed()) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.INVALID_REQUEST, "Delivery request is incomplete.");
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
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.CARGO_UNAVAILABLE,
                    "The adapter could not reserve cargo safely.");
        }

        if (prepared == null) {
            return DeliveryPreparation.failure(
                    DeliveryPreparation.Status.CARGO_UNAVAILABLE,
                    "The requested cargo is missing, stale, or already reserved.");
        }
        return DeliveryPreparation.prepared(prepared);
    }
}
