package com.innygr.icreateordernetworking.delivery;

import java.util.EnumMap;
import java.util.Map;
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

/** Shared server-side entry point for all delivery routes. Call on the logical server thread only. */
public final class DeliveryService {
    private static final Map<DeliveryRoute, AutomatedDeliveryAdapter> ADAPTERS = new EnumMap<>(DeliveryRoute.class);

    private DeliveryService() {}

    public static void registerAdapter(AutomatedDeliveryAdapter adapter) {
        if (adapter == null || adapter.route() == null || adapter.route() == DeliveryRoute.MANUAL) {
            throw new IllegalArgumentException("Only a non-manual delivery adapter can be registered");
        }
        ADAPTERS.put(adapter.route(), adapter);
    }

    public static DeliveryResult deliverManually(ServerPlayer player, UUID orderId, int count, UUID transactionId) {
        if (player == null || orderId == null || count < 1 || transactionId == null
                || !(player.level() instanceof ServerLevel level)) return DeliveryResult.INVALID_REQUEST;
        OrderData data = OrderData.get(level);
        if (data.hasProcessedDelivery(transactionId)
                || !data.recordDelivery(transactionId, orderId, DeliveryRoute.MANUAL)) return DeliveryResult.ORDER_REJECTED;
        if (!OrderService.fulfil(player, orderId, count)) {
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
                || request.route() == DeliveryRoute.MANUAL) return DeliveryResult.INVALID_REQUEST;

        OrderData data = OrderData.get(level);
        DeliveryReceipt prior = data.deliveryReceipt(request.transactionId());
        boolean recovering = prior != null;
        if (recovering) {
            if (!prior.matches(request.orderId(), request.route(), request.actorId(), request.count())) {
                return DeliveryResult.ORDER_REJECTED;
            }
            if (prior.progressApplied()) return DeliveryResult.DUPLICATE;
        }

        Order order = data.find(request.orderId());
        if (order == null || !order.itemId().equals(request.itemId())) return DeliveryResult.ORDER_REJECTED;
        if (order.claimedBy() == null || !order.claimedBy().equals(request.actorId())) {
            return DeliveryResult.ROUTE_NOT_AUTHORIZED;
        }
        if (!recovering && ((order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.OPEN
                && order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.IN_PROGRESS)
                || request.count() > order.remainingCount())) return DeliveryResult.ORDER_REJECTED;

        var requestedItem = BuiltInRegistries.ITEM.get(request.itemId());
        if (requestedItem == null || BuiltInRegistries.ITEM.getKey(requestedItem).equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return DeliveryResult.INVALID_REQUEST;
        }
        AutomatedDeliveryAdapter adapter = ADAPTERS.get(request.route());
        if (adapter == null || !adapter.isAvailable()) return DeliveryResult.ROUTE_UNAVAILABLE;
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
                && (!CreateCompat.isAeronauticsStackAvailable() || !AeronauticsDockingCompat.isDockingConnectorAvailable())) {
            return DeliveryResult.ROUTE_UNAVAILABLE;
        }
        if (request.route() == DeliveryRoute.FROGPORT && !CreateFrogportCompat.isPackageFrogportAvailable()) {
            return DeliveryResult.ROUTE_UNAVAILABLE;
        }
        if (!adapter.isAuthorized(level, request)) return DeliveryResult.ROUTE_NOT_AUTHORIZED;

        PreparedDelivery prepared;
        try {
            prepared = adapter.prepareCargo(level, request);
        } catch (RuntimeException | LinkageError exception) {
            return DeliveryResult.ORDER_REJECTED;
        }
        if (prepared == null) return DeliveryResult.ORDER_REJECTED;

        long reward = (long) request.count() * order.rewardPerItem();
        if (reward > 0 && !EconomyService.canCreditOnce(level, request.actorId(), reward, request.transactionId())) {
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        if (!recovering && !data.reserveDelivery(request.transactionId(), request.orderId(), request.route(),
                request.actorId(), request.count())) {
            prepared.rollback();
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
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        if (reward > 0 && !EconomyService.creditOnce(level, request.actorId(), reward, request.transactionId())) {
            prepared.rollback();
            return DeliveryResult.ORDER_REJECTED;
        }

        // Pending receipts are retried after interruptions; once marked applied,
        // the same transaction can never advance order progress a second time.
        if (!OrderService.recordAutomatedFulfilment(level, request.orderId(), request.actorId(), request.count())) {
            return DeliveryResult.ORDER_REJECTED;
        }
        data.markDeliveryProgressApplied(request.transactionId());
        return DeliveryResult.SUCCESS;
    }
}
