package com.innygr.icreateordernetworking.network;

import java.util.UUID;

import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to claim an order; ownership and state are checked on the server. */
public record ClaimOrderPacket(String orderId) implements CustomPacketPayload {
    public static final Type<ClaimOrderPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("icreate_order_networking", "claim_order"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimOrderPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClaimOrderPacket::orderId, ClaimOrderPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClaimOrderPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (packet.orderId() == null || packet.orderId().length() > 36) return;
            try {
                OrderService.claim(player, UUID.fromString(packet.orderId()));
            } catch (IllegalArgumentException ignored) {
                // Malformed client input is rejected without affecting the server.
            }
        });
    }
}
