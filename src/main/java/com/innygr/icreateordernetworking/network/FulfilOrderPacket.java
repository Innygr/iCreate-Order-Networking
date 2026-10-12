package com.innygr.icreateordernetworking.network;

import java.util.UUID;

import com.innygr.icreateordernetworking.delivery.DeliveryService;
import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Manual-delivery request with a stable transaction ID for retry deduplication. */
public record FulfilOrderPacket(String orderId, int count, String transactionId) implements CustomPacketPayload {
    public static final Type<FulfilOrderPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("icreate_order_networking", "fulfil_order"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FulfilOrderPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FulfilOrderPacket::orderId,
                    ByteBufCodecs.VAR_INT, FulfilOrderPacket::count,
                    ByteBufCodecs.STRING_UTF8, FulfilOrderPacket::transactionId,
                    FulfilOrderPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FulfilOrderPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (packet.orderId() == null || packet.orderId().length() > 36
                    || packet.transactionId() == null || packet.transactionId().length() > 36
                    || packet.count() < 1 || packet.count() > OrderService.MAX_ORDER_COUNT) return;
            try {
                DeliveryService.deliverManually(player, UUID.fromString(packet.orderId()),
                        packet.count(), UUID.fromString(packet.transactionId()));
            } catch (IllegalArgumentException ignored) {
                // Reject malformed client input.
            }
        });
    }
}
