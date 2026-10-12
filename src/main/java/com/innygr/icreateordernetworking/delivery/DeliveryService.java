package com.innygr.icreateordernetworking.delivery;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.innygr.icreateordernetworking.compat.CreateCompat;
import com.innygr.icreateordernetworking.economy.EconomyService;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared server-side entry point for all delivery routes.
 *
 * Manual delivery is live through OrderService. Automated routes fail closed
 * until their adapter can participate in a single atomic cargo + order + payout
 * transaction. Never consume automated cargo before that transaction exists.
 *
 * Call from the logical server thread only.
 */
public final class DeliveryService {
    private static final Map<DeliveryRoute, AutomatedDeliveryAdapter> ADAPTERS =
            new EnumMap<>(DeliveryRoute.class);

    private DeliveryService() {}

    public static void registerAdapter(AutomatedDeliveryAdapter adapter) {
        if (adapter == null || adapter.route() == null || adapter.route() == DeliveryRoute.MANUAL) {
            throw new IllegalArgumentException("Only a non-manual delivery adapter can be registered");
        }
        ADAPTERS.put(adapter.route(), adapter);
    }

    /**
     * Manual handoff with a durable transaction ID. The caller must reuse the
     * same ID if it retries the same submission (for example after a UI timeout).
     */
    public static DeliveryResult deliverManually(ServerPlayer player, UUID orderId,
                                                 int count, UUID transactionId) {
        if (player == null || orderId == null || count < 1 || transactionId == null
                || !(player.level() instanceof ServerLevel level)) {
            return DeliveryResult.INVALID_REQUEST;
        }

        OrderData data = OrderData.get(level);
        if (data.hasProcessedDelivery(transactionId)) return DeliveryResult.ORDER_REJECTED;
        if (!data.recordDelivery(transactionId, orderId, DeliveryRoute.MANUAL)) {
            return DeliveryResult.ORDER_REJECTED;
        }

        if (!OrderService.fulfil(player, orderId, count)) {
            // No fulfilment happened, so allow a legitimate retry with the same ID.
            data.forgetDelivery(transactionId);
            return DeliveryResult.ORDER_REJECTED;
        }
        return DeliveryResult.SUCCESS;
    }

    /**
     * Convenience overload for one-shot callers. Network/UI code should use the
     * transaction-ID overload to make retries idempotent.
     */
    public static DeliveryResult deliverManually(ServerPlayer player, UUID orderId, int count) {
        return deliverManually(player, orderId, count, UUID.randomUUID());
    }

    /**
     * Automated delivery is intentionally fail-closed for now. A working adapter
     * needs to atomically reserve/consume cargo, advance order state, credit the
     * verified beneficiary, and persist an idempotency receipt. Splitting those
     * steps would risk lost cargo or duplicate rewards on retries/restarts.
     */
    public static DeliveryResult deliverAutomated(ServerLevel level, DeliveryRequest request) {
        if (level == null || request == null || !request.isWellFormed()
                || request.route() == DeliveryRoute.MANUAL) return DeliveryResult.INVALID_REQUEST;

        OrderData data = OrderData.get(level);
        if (data.hasProcessedDelivery(request.transactionId())) return DeliveryResult.ORDER_REJECTED;

        Order order = data.find(request.orderId());
        if (order == null || (order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.OPEN
                && order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.IN_PROGRESS)
                || !order.itemId().equals(request.itemId())
                || request.count() > order.remainingCount()) return DeliveryResult.ORDER_REJECTED;
        if (order.claimedBy() == null || !order.claimedBy().equals(request.actorId())) {
            return DeliveryResult.ROUTE_NOT_AUTHORIZED;
        }

        var requestedItem = BuiltInRegistries.ITEM.get(request.itemId());
        if (requestedItem == null
                || BuiltInRegistries.ITEM.getKey(requestedItem).equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return DeliveryResult.INVALID_REQUEST;
        }

        AutomatedDeliveryAdapter adapter = ADAPTERS.get(request.route());
        if (adapter == null || !adapter.isAvailable()) return DeliveryResult.ROUTE_UNAVAILABLE;

        if ((request.route() == DeliveryRoute.AIRSHIP || request.route() == DeliveryRoute.TRAIN
                || request.route() == DeliveryRoute.FROGPORT)
                && !CreateCompat.isCreateLoaded()) return DeliveryResult.ROUTE_UNAVAILABLE;
        if (request.route() == DeliveryRoute.AIRSHIP && !CreateCompat.isAeronauticsStackAvailable()) {
            return DeliveryResult.ROUTE_UNAVAILABLE;
        }
        if (!adapter.isAuthorized(level, request)) return DeliveryResult.ROUTE_NOT_AUTHORIZED;

        PreparedDelivery prepared;
        try {
            prepared = adapter.prepareCargo(level, request);
        } catch (RuntimeException exception) {
            return DeliveryResult.ORDER_REJECTED;
        }
        if (prepared == null) return DeliveryResult.ORDER_REJECTED;

        long reward = (long) request.count() * order.rewardPerItem();
        if (reward > 0 && !EconomyService.canCredit(level, request.actorId(), reward)) {
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        // Receipt reservation, payout, cargo commit, and progress finalization all
        // execute on the server thread. Adapters must support rollback after commit
        // and make commit idempotent for this transaction ID.
        if (!data.recordDelivery(request.transactionId(), request.orderId(), request.route())) {
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        boolean credited = reward == 0 || EconomyService.credit(level, request.actorId(), reward);
        if (!credited) {
            data.forgetDelivery(request.transactionId());
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        boolean cargoCommitted;
        try {
            cargoCommitted = prepared.commit();
        } catch (RuntimeException exception) {
            cargoCommitted = false;
        }
        if (!cargoCommitted) {
            if (reward > 0) EconomyService.debit(level, request.actorId(), reward);
            data.forgetDelivery(request.transactionId());
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        if (!OrderService.recordAutomatedFulfilment(
                level, request.orderId(), request.actorId(), request.count())) {
            if (reward > 0) EconomyService.debit(level, request.actorId(), reward);
            data.forgetDelivery(request.transactionId());
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        return DeliveryResult.SUCCESS;
    }
}
