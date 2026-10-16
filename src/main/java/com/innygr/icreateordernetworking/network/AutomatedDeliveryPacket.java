package com.innygr.icreateordernetworking.network;

import java.util.UUID;

import com.innygr.icreateordernetworking.delivery.AutomatedDeliveryService;
import com.innygr.icreateordernetworking.delivery.DeliveryPreparation;
import com.innygr.icreateordernetworking.delivery.DeliveryRequest;
import com.innygr.icreateordernetworking.delivery.DeliveryRoute;
import com.innygr.icreateordernetworking.delivery.FactorySourcedDeliveryService;
import com.innygr.icreateordernetworking.logistics.StockLinkBindingData;
import com.innygr.icreateordernetworking.delivery.DurableShipmentService;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.permission.OrderPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-authoritative request to start a durable automated shipment.
 * The client supplies endpoint coordinates and a transaction ID only; order
 * item, claimant identity, route authorization, and physical cargo are resolved
 * or checked again on the server.
 */
public record AutomatedDeliveryPacket(BlockPos terminalPos, String orderId, int count,
                                      String route, String sourceReference,
                                      String transactionId) implements CustomPacketPayload {
    public static final Type<AutomatedDeliveryPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("icreate_order_networking", "automated_delivery"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutomatedDeliveryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AutomatedDeliveryPacket::terminalPos,
                    ByteBufCodecs.STRING_UTF8, AutomatedDeliveryPacket::orderId,
                    ByteBufCodecs.VAR_INT, AutomatedDeliveryPacket::count,
                    ByteBufCodecs.STRING_UTF8, AutomatedDeliveryPacket::route,
                    ByteBufCodecs.STRING_UTF8, AutomatedDeliveryPacket::sourceReference,
                    ByteBufCodecs.STRING_UTF8, AutomatedDeliveryPacket::transactionId,
                    AutomatedDeliveryPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AutomatedDeliveryPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> start(player, packet));
    }

    private static void start(ServerPlayer player, AutomatedDeliveryPacket packet) {
        if (!(player.level() instanceof ServerLevel level)
                || !OrderPermissions.canUseTerminal(player, packet.terminalPos())) {
            reply(player, "Automated delivery denied: terminal access is required.");
            return;
        }
        if (packet.orderId() == null || packet.orderId().length() > 36
                || packet.transactionId() == null || packet.transactionId().length() > 36
                || packet.route() == null || packet.route().length() > 32
                || packet.sourceReference() == null || packet.sourceReference().isBlank()
                || packet.sourceReference().length() > 256
                || packet.count() < 1 || packet.count() > com.innygr.icreateordernetworking.orders.OrderService.MAX_ORDER_COUNT) {
            reply(player, "Automated delivery rejected: invalid request fields.");
            return;
        }

        final UUID orderId;
        final UUID transactionId;
        final DeliveryRoute route;
        try {
            orderId = UUID.fromString(packet.orderId());
            transactionId = UUID.fromString(packet.transactionId());
            route = DeliveryRoute.valueOf(packet.route());
        } catch (IllegalArgumentException ignored) {
            reply(player, "Automated delivery rejected: invalid order, transaction, or route.");
            return;
        }
        Order order = OrderData.get(level).find(orderId);
        if (order == null || order.itemId() == null) {
            reply(player, "Automated delivery failed: order not found.");
            return;
        }
        ResourceLocation registeredItem = order.itemId();
        if (!BuiltInRegistries.ITEM.containsKey(registeredItem)) {
            reply(player, "Automated delivery failed: the order item is unavailable.");
            return;
        }

        DeliveryRequest request = new DeliveryRequest(orderId, registeredItem, packet.count(), route,
                player.getUUID(), packet.sourceReference(), transactionId);

        // A bound Stock Link beside the Order Terminal opts this request into
        // authorized factory sourcing. Without one, retain the direct-logger path.
        BlockPos factoryStockLink = findAdjacentAuthorizedStockLink(level, packet.terminalPos(), player);
        DeliveryPreparation preparation = route == DeliveryRoute.FROGPORT && factoryStockLink != null
                ? FactorySourcedDeliveryService.prepare(level, request, factoryStockLink, packet.terminalPos())
                : AutomatedDeliveryService.prepare(level, request);
        if (preparation.status() != DeliveryPreparation.Status.PREPARED) {
            reply(player, "Automated delivery failed: " + preparation.detail());
            return;
        }

        if (DurableShipmentService.commitDispatch(level, transactionId, preparation.preparedDelivery())) {
            reply(player, "Shipment dispatched. Order progress and reward wait for verified destination receipt.");
        } else {
            // Keep the durable reservation: recovery may finish a partially prepared
            // source after a transient PSI/transport interruption or server restart.
            reply(player, "Shipment reserved but not yet dispatched. Keep the source logger loaded; recovery will retry.");
        }
    }


    private static BlockPos findAdjacentAuthorizedStockLink(ServerLevel level, BlockPos terminalPos,
                                                             ServerPlayer player) {
        if (level == null || terminalPos == null || player == null) return null;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos candidate = terminalPos.relative(direction);
            if (!level.hasChunkAt(candidate)) continue;
            if (StockLinkBindingData.get(level).authorizedBinding(level, candidate, player.getUUID()) != null) {
                return candidate;
            }
        }
        return null;
    }

    private static void reply(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
