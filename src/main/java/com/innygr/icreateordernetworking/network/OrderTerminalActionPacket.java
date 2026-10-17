package com.innygr.icreateordernetworking.network;

import java.util.UUID;
import com.innygr.icreateordernetworking.orders.DeliveryService;
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
                case OPEN -> {
                    java.util.List<Order> visibleOrders = OrderService.listFor(player);
                    player.displayClientMessage(Component.literal("Order Terminal: "
                            + visibleOrders.size() + " active order(s) available to you."), false);
                    int shown = Math.min(visibleOrders.size(), 8);
                    for (int i = 0; i < shown; i++) {
                        Order order = visibleOrders.get(i);
                        player.displayClientMessage(Component.literal(
                                order.id() + " | " + order.itemId() + " | remaining "
                                        + order.remainingCount() + " | " + order.status().name()), false);
                    }
                    if (visibleOrders.size() > shown) {
                        player.displayClientMessage(Component.literal("Showing " + shown + " of "
                                + visibleOrders.size() + " orders. Use the order UUID for actions."), false);
                    }
                }
                case CREATE -> create(player, packet);
                case CLAIM -> withOrderId(packet.orderId(), id -> OrderService.claim(player, id), player);
                case FULFIL -> fulfil(player, packet);
                case CANCEL -> withOrderId(packet.orderId(), id -> OrderService.cancel(player, id), player);
                default -> { }
            }
        });
    }

    private static void create(ServerPlayer player, OrderTerminalActionPacket packet) {
        if (packet.itemId() == null || packet.itemId().isBlank() || packet.itemId().length() > 256
                || packet.count() < 1 || packet.count() > OrderService.MAX_ORDER_COUNT
                || packet.rewardPerItem() < 0 || packet.rewardPerItem() > OrderService.MAX_REWARD_PER_ITEM) {
            player.displayClientMessage(Component.literal(
                    "Order creation rejected: enter a valid item ID, count, and reward."), true);
            return;
        }
        final ResourceLocation requested;
        try { requested = ResourceLocation.parse(packet.itemId()); }
        catch (RuntimeException ignored) {
            player.displayClientMessage(Component.literal(
                    "Order creation rejected: item ID must look like minecraft:stone."), true);
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && requested.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                Order order = OrderService.create(player, stack, packet.count(), packet.rewardPerItem());
                if (order == null) {
                    player.displayClientMessage(Component.literal(
                            "Order creation failed: check your balance and order settings."), true);
                } else {
                    // Keep the UUID in chat so it remains available after the
                    // action-bar notification disappears and can be entered in the terminal UI.
                    player.displayClientMessage(Component.literal("Order created: " + order.id()), false);
                }
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
