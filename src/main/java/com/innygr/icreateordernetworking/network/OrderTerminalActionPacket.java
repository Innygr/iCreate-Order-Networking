package com.innygr.icreateordernetworking.network;

import java.util.UUID;
import com.innygr.icreateordernetworking.delivery.DeliveryService;
import com.innygr.icreateordernetworking.delivery.DeliveryResult;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderService;
import com.innygr.icreateordernetworking.permission.OrderPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Terminal actions bound to a reachable terminal and its current owner/team ACL. */
public record OrderTerminalActionPacket(BlockPos terminalPos, int action, String itemId,
                                        int count, int rewardPerItem, String orderId,
                                        String transactionId) implements CustomPacketPayload {
    public static final int OPEN = 0, CREATE = 1, CLAIM = 2, FULFIL = 3, CANCEL = 4;
    public static final Type<OrderTerminalActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("icreate_order_networking", "terminal_action"));
    // StreamCodec.composite overloads support at most six fields in this target
    // Minecraft version, so encode this seven-field payload explicitly.
    public static final StreamCodec<RegistryFriendlyByteBuf, OrderTerminalActionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buf, OrderTerminalActionPacket packet) -> {
                        BlockPos.STREAM_CODEC.encode(buf, packet.terminalPos());
                        ByteBufCodecs.VAR_INT.encode(buf, packet.action());
                        ByteBufCodecs.STRING_UTF8.encode(buf, packet.itemId());
                        ByteBufCodecs.VAR_INT.encode(buf, packet.count());
                        ByteBufCodecs.VAR_INT.encode(buf, packet.rewardPerItem());
                        ByteBufCodecs.STRING_UTF8.encode(buf, packet.orderId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, packet.transactionId());
                    },
                    (RegistryFriendlyByteBuf buf) -> new OrderTerminalActionPacket(
                            BlockPos.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OrderTerminalActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (packet.action() < OPEN || packet.action() > CANCEL
                    || !OrderPermissions.canUseTerminal(player, packet.terminalPos())) {
                player.displayClientMessage(Component.literal("Order Terminal access denied or terminal out of reach."), true);
                return;
            }
            if (!(player.level() instanceof ServerLevel)) return;
            switch (packet.action()) {
                case OPEN -> player.displayClientMessage(Component.literal("Order Terminal: "
                        + OrderService.listFor(player).size() + " active order(s) available to you."), true);
                case CREATE -> create(player, packet);
                case CLAIM -> withOrderId(packet.orderId(), id -> OrderService.claim(player, id), player);
                case FULFIL -> fulfil(player, packet);
                case CANCEL -> withOrderId(packet.orderId(), id -> OrderService.cancel(player, id), player);
                default -> { }
            }
        });
    }

    private static void create(ServerPlayer player, OrderTerminalActionPacket packet) {
        if (packet.itemId() == null || packet.itemId().length() > 256
                || packet.count() < 1 || packet.count() > OrderService.MAX_ORDER_COUNT
                || packet.rewardPerItem() < 0 || packet.rewardPerItem() > OrderService.MAX_REWARD_PER_ITEM) return;
        final ResourceLocation requested;
        try { requested = ResourceLocation.parse(packet.itemId()); }
        catch (RuntimeException ignored) { return; }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && requested.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                Order order = OrderService.create(player, stack, packet.count(), packet.rewardPerItem());
                player.displayClientMessage(Component.literal(order == null
                        ? "Order creation failed: check your balance and order settings."
                        : "Order created: " + order.id()), true);
                return;
            }
        }
        player.displayClientMessage(Component.literal("You must have at least one requested item in your inventory."), true);
    }

    private static void fulfil(ServerPlayer player, OrderTerminalActionPacket packet) {
        if (packet.orderId() == null || packet.orderId().length() > 36
                || packet.transactionId() == null || packet.transactionId().length() > 36
                || packet.count() < 1 || packet.count() > OrderService.MAX_ORDER_COUNT) return;
        try {
            DeliveryResult result = DeliveryService.deliverInventory(player, UUID.fromString(packet.orderId()),
                    packet.count(), UUID.fromString(packet.transactionId()));
            boolean success = result == DeliveryResult.SUCCESS;
            player.displayClientMessage(Component.literal(success ? "Order delivery recorded."
                    : "Delivery failed: " + result.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')), true);
        } catch (IllegalArgumentException ignored) { }
    }

    private static void withOrderId(String value, java.util.function.Function<UUID, Boolean> action,
                                    ServerPlayer player) {
        if (value == null || value.length() > 36) return;
        try {
            boolean success = action.apply(UUID.fromString(value));
            player.displayClientMessage(Component.literal(success ? "Order action completed."
                    : "Order action denied or the order is no longer available."), true);
        } catch (IllegalArgumentException ignored) { }
    }
}
