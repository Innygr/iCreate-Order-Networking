package com.innygr.icreateordernetworking.network;

import java.util.UUID;

import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to create an order; all item and balance checks run server-side. */
public record CreateOrderPacket(String itemId, int count, int rewardPerItem) implements CustomPacketPayload {
    public static final Type<CreateOrderPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("icreate_order_networking", "create_order"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateOrderPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CreateOrderPacket::itemId,
                    ByteBufCodecs.VAR_INT, CreateOrderPacket::count,
                    ByteBufCodecs.VAR_INT, CreateOrderPacket::rewardPerItem,
                    CreateOrderPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CreateOrderPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (packet.itemId() == null || packet.itemId().length() > 256
                    || packet.count() < 1 || packet.count() > OrderService.MAX_ORDER_COUNT
                    || packet.rewardPerItem() < 0
                    || packet.rewardPerItem() > OrderService.MAX_REWARD_PER_ITEM) return;
            final ResourceLocation id;
            try {
                id = ResourceLocation.parse(packet.itemId());
            } catch (RuntimeException exception) {
                return;
            }

            // The client can request an item ID, but cannot conjure it: the server
            // selects a matching stack that actually exists in the player's inventory.
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && id.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    OrderService.create(player, stack, packet.count(), packet.rewardPerItem());
                    return;
                }
            }
        });
    }
}
