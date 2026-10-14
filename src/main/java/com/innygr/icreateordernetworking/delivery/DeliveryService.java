package com.innygr.icreateordernetworking.delivery;

import java.util.UUID;

import com.innygr.icreateordernetworking.compat.CreateCompat;
import com.innygr.icreateordernetworking.compat.CreateBeltCompat;
import com.innygr.icreateordernetworking.compat.CreateFrogportCompat;
import com.innygr.icreateordernetworking.compat.CreateTrainCompat;
import com.innygr.icreateordernetworking.compat.AeronauticsDockingCompat;
import com.innygr.icreateordernetworking.economy.EconomyService;
import com.innygr.icreateordernetworking.orders.DeliveryReceipt;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared server-side entry point for all delivery routes. Call on the logical server thread only. */
public final class DeliveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryService.class);

    private DeliveryService() {}

    /**
     * Registers a verified optional transport adapter. Broken or unavailable
     * optional integrations are logged and skipped rather than taking down startup.
     */
    public static void registerAdapter(AutomatedDeliveryAdapter adapter) {
        if (adapter == null) throw new IllegalArgumentException("Adapter cannot be null");
        DeliveryRoute route;
        try {
            route = adapter.route();
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.warn("Skipping delivery adapter whose route API is unavailable", exception);
            return;
        }
        if (route == null || route == DeliveryRoute.MANUAL) {
            LOGGER.warn("Skipping invalid automated delivery adapter route: {}", route);
            return;
        }
        if (!AutomatedDeliveryRegistry.register(adapter)) {
            LOGGER.warn("Skipping unavailable or duplicate delivery adapter for route {}", route);
        }
    }

    public static DeliveryResult deliverManually(ServerPlayer player, UUID orderId, int count, UUID transactionId) {
        if (player == null || orderId == null || count < 1 || transactionId == null
                || !(player.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return DeliveryResult.INVALID_REQUEST;
        OrderData data = OrderData.get(level);
        if (data.hasProcessedDelivery(transactionId)
                || !data.recordDelivery(transactionId, orderId, DeliveryRoute.MANUAL)) return DeliveryResult.ORDER_REJECTED;
        if (!OrderService.fulfil(player, orderId, count, transactionId)) {
            data.forgetDelivery(transactionId);
            return DeliveryResult.ORDER_REJECTED;
        }
        return DeliveryResult.SUCCESS;
    }

    public static DeliveryResult deliverManually(ServerPlayer player, UUID orderId, int count) {
        return deliverManually(player, orderId, count, UUID.randomUUID());
    }

    /**
     * Automated delivery uses a durable pending receipt. On retry, the adapter
     * must make commit idempotent for request.transactionId(); a pending receipt
     * is resumed instead of being rejected or paying twice.
     */
    public static DeliveryResult deliverAutomated(ServerLevel level, DeliveryRequest request) {
        if (level == null || request == null || !request.isWellFormed()
                || request.route() == DeliveryRoute.MANUAL
                || !level.getServer().isSameThread()) return DeliveryResult.INVALID_REQUEST;

        OrderData data = OrderData.get(level);
        DeliveryReceipt prior = data.deliveryReceipt(request.transactionId());
        boolean recovering = prior != null;
        if (recovering) {
            if (!prior.matches(request.orderId(), request.route(), request.actorId(), request.count())) {
                return DeliveryResult.ORDER_REJECTED;
            }
            if (prior.progressApplied()) return DeliveryResult.DUPLICATE;
            // Crash window: order progress may already be durable while receipt
            // finalization is not. Close that window before preparing/committing
            // cargo again, otherwise a non-idempotent adapter could ship twice.
            if (data.hasAppliedAutomatedProgress(request.transactionId())) {
                data.markDeliveryProgressApplied(request.transactionId());
                return DeliveryResult.SUCCESS;
            }
        }

        Order order = data.find(request.orderId());
        if (order == null || !order.itemId().equals(request.itemId())) return DeliveryResult.ORDER_REJECTED;
        if (order.claimedBy() == null || !order.claimedBy().equals(request.actorId())) {
            return DeliveryResult.ROUTE_NOT_AUTHORIZED;
        }
        if (!recovering && ((order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.OPEN
                && order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.IN_PROGRESS)
                || request.count() > order.remainingCount()
                        - data.pendingDeliveryCountForOrder(order.id(), null))) {
            return DeliveryResult.ORDER_REJECTED;
        }

        var requestedItem = BuiltInRegistries.ITEM.get(request.itemId());
        if (requestedItem == null
                || BuiltInRegistries.ITEM.getKey(requestedItem).equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return DeliveryResult.INVALID_REQUEST;
        }

        AutomatedDeliveryAdapter adapter = AutomatedDeliveryRegistry.find(request.route()).orElse(null);
        if (adapter == null) return DeliveryResult.ROUTE_UNAVAILABLE;

        try {
            if ((request.route() == DeliveryRoute.AIRSHIP || request.route() == DeliveryRoute.TRAIN
                    || request.route() == DeliveryRoute.FROGPORT) && !CreateCompat.isCreateLoaded()) {
                return DeliveryResult.ROUTE_UNAVAILABLE;
            }
            if (request.route() == DeliveryRoute.BELT && !CreateBeltCompat.isBeltInfrastructureAvailable()) {
                return DeliveryResult.ROUTE_UNAVAILABLE;
            }
            if (request.route() == DeliveryRoute.TRAIN && !CreateTrainCompat.isPortableStorageInterfaceAvailable()) {
                return DeliveryResult.ROUTE_UNAVAILABLE;
            }
            if (request.route() == DeliveryRoute.AIRSHIP
                    && (!CreateCompat.isAeronauticsStackAvailable()
                    || !AeronauticsDockingCompat.isDockingConnectorAvailable())) {
                return DeliveryResult.ROUTE_UNAVAILABLE;
            }
            if (request.route() == DeliveryRoute.FROGPORT && !CreateFrogportCompat.isPackageFrogportAvailable()) {
                return DeliveryResult.ROUTE_UNAVAILABLE;
            }
            if (!adapter.isAuthorized(level, request)) return DeliveryResult.ROUTE_NOT_AUTHORIZED;
        } catch (RuntimeException | LinkageError exception) {
            return DeliveryResult.ROUTE_UNAVAILABLE;
        }

        PreparedDelivery prepared;
        try {
            prepared = adapter.prepareCargo(level, request);
        } catch (RuntimeException | LinkageError exception) {
            return DeliveryResult.ORDER_REJECTED;
        }
        if (prepared == null) return DeliveryResult.ORDER_REJECTED;

        long reward = (long) request.count() * order.rewardPerItem();
        if (reward > 0 && !EconomyService.canCreditOnce(level, request.actorId(), reward, request.transactionId())) {
            safeRollback(prepared);
            return DeliveryResult.ORDER_REJECTED;
        }

        if (!recovering && !data.reserveDelivery(request.transactionId(), request.orderId(), request.route(),
                request.actorId(), request.count())) {
            safeRollback(prepared);
            return DeliveryResult.ORDER_REJECTED;
        }

        boolean cargoCommitted;
        try {
            cargoCommitted = prepared.commit();
        } catch (RuntimeException | LinkageError exception) {
            cargoCommitted = false;
        }
        if (!cargoCommitted) {
            // Keep the pending receipt: an adapter may be able to recover its reservation on retry.
            safeRollback(prepared);
            return DeliveryResult.ORDER_REJECTED;
        }

        if (reward > 0 && !EconomyService.creditOnce(level, request.actorId(), reward, request.transactionId())) {
            safeRollback(prepared);
            return DeliveryResult.ORDER_REJECTED;
        }

        if (!OrderService.recordAutomatedFulfilment(level, request.orderId(), request.route(), request.actorId(), request.count(), request.transactionId())) {
            return DeliveryResult.ORDER_REJECTED;
        }
        data.markDeliveryProgressApplied(request.transactionId());
        return DeliveryResult.SUCCESS;
    }

    private static void safeRollback(PreparedDelivery prepared) {
        try {
            prepared.rollback();
        } catch (RuntimeException | LinkageError ignored) {
            // A broken optional adapter must not crash the server during cleanup.
        }
    }
}
