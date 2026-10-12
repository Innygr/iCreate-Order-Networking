package com.innygr.icreateordernetworking.delivery;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.innygr.icreateordernetworking.compat.CreateCompat;
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

    public static DeliveryResult deliverManually(ServerPlayer player, UUID orderId, int count) {
        if (player == null || orderId == null || count < 1) return DeliveryResult.INVALID_REQUEST;
        return OrderService.fulfil(player, orderId, count)
                ? DeliveryResult.SUCCESS : DeliveryResult.ORDER_REJECTED;
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

        Order order = OrderData.get(level).find(request.orderId());
        if (order == null || !order.itemId().equals(request.itemId())
                || request.count() > order.remainingCount()) return DeliveryResult.ORDER_REJECTED;

        if (BuiltInRegistries.ITEM.get(request.itemId()) == BuiltInRegistries.ITEM.getDefaultKey()) {
            return DeliveryResult.INVALID_REQUEST;
        }

        AutomatedDeliveryAdapter adapter = ADAPTERS.get(request.route());
        if (adapter == null || !adapter.isAvailable()) return DeliveryResult.ROUTE_UNAVAILABLE;

        if ((request.route() == DeliveryRoute.AIRSHIP || request.route() == DeliveryRoute.TRAIN)
                && !CreateCompat.isCreateLoaded()) return DeliveryResult.ROUTE_UNAVAILABLE;
        if (request.route() == DeliveryRoute.AIRSHIP && !CreateCompat.isAeronauticsStackAvailable()) {
            return DeliveryResult.ROUTE_UNAVAILABLE;
        }
        if (!adapter.isAuthorized(level, request)) return DeliveryResult.ROUTE_NOT_AUTHORIZED;

        // Do not call consumeCargo until the shared atomic fulfilment transaction
        // can also pay and persist the receipt. Returning NOT_IMPLEMENTED preserves
        // cargo rather than silently eating it or allowing an unpaid delivery.
        return DeliveryResult.NOT_IMPLEMENTED;
    }
}
