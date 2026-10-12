package com.innygr.icreateordernetworking.orders;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Manual delivery adapter. A future GUI/network packet should call this from
 * the server thread; never accept the slot contents or delivery UUID as proof
 * from the client without revalidating them here.
 */
public final class ManualDeliveryService {
    private ManualDeliveryService() {}

    public static DeliveryResult deliver(ServerPlayer player, UUID orderId, int slot, int count) {
        if (player == null || !(player.level() instanceof ServerLevel level)
                || slot < 0 || slot >= player.getInventory().getContainerSize()
                || count < 1) return DeliveryResult.INVALID_REQUEST;

        Order order = OrderData.get(level).find(orderId);
        if (order == null) return DeliveryResult.INVALID_ORDER;
        if (order.claimedBy() == null || !player.getUUID().equals(order.claimedBy())) {
            return DeliveryResult.WRONG_CLAIMANT;
        }

        Inventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty() || stack.getCount() < count) return DeliveryResult.INSUFFICIENT_CARGO;

        // Generate the receipt on the server. A client cannot replay a receipt
        // ID to trigger a second payout.
        DeliveryResult result = DeliveryService.deliver(
                level, orderId, player.getUUID(), stack, count, DeliveryRoute.MANUAL, UUID.randomUUID());
        if (result == DeliveryResult.ACCEPTED) inventory.setChanged();
        return result;
    }
}
