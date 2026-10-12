package com.innygr.icreateordernetworking.orders;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Initial server-side order rules. This stage records claims and quantities;
 * it deliberately does not yet transfer items or pay rewards.
 */
public final class OrderService {
    public static final int MAX_ORDER_COUNT = 4096;
    public static final int MAX_REWARD_PER_ITEM = 100_000;

    private OrderService() {}

    public static Order create(ServerPlayer owner, ItemStack exampleItem, int count, int rewardPerItem) {
        if (!(owner.level() instanceof ServerLevel level) || exampleItem.isEmpty()
                || count < 1 || count > MAX_ORDER_COUNT
                || rewardPerItem < 0 || rewardPerItem > MAX_REWARD_PER_ITEM) return null;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(exampleItem.getItem());
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) return null;

        Order order = new Order(UUID.randomUUID(), owner.getUUID(), owner.getGameProfile().getName(),
                itemId, count, 0, rewardPerItem, null, OrderStatus.OPEN, level.getGameTime());
        OrderData.get(level).add(order);
        return order;
    }

    public static List<Order> list(ServerLevel level) {
        return OrderData.get(level).all().stream()
                .filter(o -> o.status() == OrderStatus.OPEN || o.status() == OrderStatus.IN_PROGRESS)
                .toList();
    }

    public static boolean claim(ServerPlayer player, UUID orderId) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        OrderData data = OrderData.get(level);
        Order order = data.find(orderId);
        if (order == null || order.ownerId().equals(player.getUUID())
                || order.status() != OrderStatus.OPEN) return false;
        order.claim(player.getUUID());
        data.changed();
        return true;
    }

    /**
     * Records a server-confirmed delivery count. Do not call from a client packet:
     * inventory removal and transaction-safe reward handling are a later step.
     */
    public static boolean recordFulfilment(ServerPlayer player, UUID orderId, int count) {
        if (!(player.level() instanceof ServerLevel level) || count < 1) return false;
        OrderData data = OrderData.get(level);
        Order order = data.find(orderId);
        if (order == null || order.status() != OrderStatus.IN_PROGRESS
                || !player.getUUID().equals(order.claimedBy()) || count > order.remainingCount()) return false;
        order.fulfil(count);
        data.changed();
        return true;
    }

    public static boolean cancel(ServerPlayer player, UUID orderId) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        OrderData data = OrderData.get(level);
        Order order = data.find(orderId);
        if (order == null || !order.ownerId().equals(player.getUUID())
                || (order.status() != OrderStatus.OPEN && order.status() != OrderStatus.IN_PROGRESS)) return false;
        order.cancel();
        data.changed();
        return true;
    }
}
