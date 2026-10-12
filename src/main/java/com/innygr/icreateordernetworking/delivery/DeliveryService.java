package com.innygr.icreateordernetworking.delivery;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.innygr.icreateordernetworking.compat.CreateCompat;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Shared server-side entry point for all delivery routes.
 *
 * The manual route uses the player's inventory implementation already provided
 * by OrderService. Automated routes are deliberately fail-closed until a verified
 * adapter for the relevant mod/API is registered.
 *
 * Call from the logical server thread only.
 */
public final class DeliveryService {
    private static final Map<DeliveryRoute, AutomatedDeliveryAdapter> ADAPTERS =
            new EnumMap<>(DeliveryRoute.class);
    private static final Set<UUID> COMPLETED_TRANSACTIONS = new HashSet<>();

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
     * Submit a delivery from an automated transport. This method does not accept
     * client packets as proof of cargo. The adapter must be called by trusted
     * server-side integration code after validating the real cargo source.
     *
     * Transaction IDs are idempotency keys for the running server session. A
     * durable receipt ledger will be needed before adapters can safely retry
     * across server restarts or chunk unload/reload boundaries.
     */
    public static DeliveryResult deliverAutomated(ServerLevel level, DeliveryRequest request) {
        if (level == null || request == null || !request.isWellFormed()
                || request.route() == DeliveryRoute.MANUAL) return DeliveryResult.INVALID_REQUEST;

        if (COMPLETED_TRANSACTIONS.contains(request.transactionId())) return DeliveryResult.SUCCESS;

        Order order = OrderData.get(level).find(request.orderId());
        if (order == null || !order.itemId().equals(request.itemId())
                || request.count() > order.remainingCount()) return DeliveryResult.ORDER_REJECTED;

        AutomatedDeliveryAdapter adapter = ADAPTERS.get(request.route());
        if (adapter == null || !adapter.isAvailable()) return DeliveryResult.ROUTE_UNAVAILABLE;

        if ((request.route() == DeliveryRoute.AIRSHIP || request.route() == DeliveryRoute.TRAIN)
                && !CreateCompat.isCreateLoaded()) return DeliveryResult.ROUTE_UNAVAILABLE;
        if (request.route() == DeliveryRoute.AIRSHIP && !CreateCompat.isAeronauticsStackAvailable()) {
            return DeliveryResult.ROUTE_UNAVAILABLE;
        }

        if (!adapter.isAuthorized(level, request)) return DeliveryResult.ROUTE_NOT_AUTHORIZED;

        // Validate the order and item before asking the adapter to consume cargo.
        if (BuiltInRegistries.ITEM.get(request.itemId()) == BuiltInRegistries.ITEM.getDefaultKey()) {
            return DeliveryResult.INVALID_REQUEST;
        }
        if (!adapter.consumeCargo(level, request)) return DeliveryResult.ORDER_REJECTED;

        // Automated cargo has no implicit player inventory/claimant. Until the
        // order model supports a verified delivery beneficiary and a shared
        // cargo-consumption fulfilment transaction, do not mark it fulfilled or
        // pay rewards here. Adapters must not be enabled for production yet.
        return DeliveryResult.NOT_IMPLEMENTED;
    }

    /** Test/diagnostic helper; a production durable receipt store is still needed. */
    static boolean hasCompletedTransaction(UUID transactionId) {
        return COMPLETED_TRANSACTIONS.contains(transactionId);
    }

    private static ItemStack copyItem(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }
}
