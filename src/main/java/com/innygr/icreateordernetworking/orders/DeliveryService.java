package com.innygr.icreateordernetworking.orders;

import java.util.UUID;

import com.innygr.icreateordernetworking.economy.EconomyService;
import com.innygr.icreateordernetworking.delivery.DeliveryResult;
import com.innygr.icreateordernetworking.delivery.DeliveryRoute;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared server-side entry point for every delivery method. */
public final class DeliveryService {
    private DeliveryService() {}

    /**
     * Accepts a real mutable stack from a trusted server-owned transport source.
     * The route adapter must verify the shipment source before calling this method.
     * All calls must run on the logical server thread; never call it from a client packet.
     */
    public static DeliveryResult deliver(ServerLevel level, UUID orderId, UUID claimantId,
                                         ItemStack cargo, int count, DeliveryRoute route,
                                         UUID deliveryId) {
        if (level == null || orderId == null || claimantId == null || cargo == null
                || cargo.isEmpty() || count < 1 || route == null || deliveryId == null) {
            return DeliveryResult.INVALID_REQUEST;
        }
        OrderData data = OrderData.get(level);
        if (data.hasProcessedDelivery(deliveryId)) return DeliveryResult.DUPLICATE;

        Order order = data.find(orderId);
        DeliveryResult validation = validateOrder(order, claimantId, count);
        if (validation != DeliveryResult.SUCCESS) return validation;
        if (cargo.getCount() < count) return DeliveryResult.INSUFFICIENT_CARGO;

        Item item = resolveItem(order.itemId());
        if (item == null || !cargo.is(item)) return DeliveryResult.WRONG_ITEM;

        long reward = (long) count * order.rewardPerItem();
        // Use the same durable transaction ID for the payout and delivery receipt.
        // This protects the currency ledger against duplicate credits if a delivery
        // is retried after an interrupted save or an adapter retry.
        if (reward > 0 && !EconomyService.canCreditOnce(level, claimantId, reward, deliveryId)) {
            return DeliveryResult.PAYOUT_LIMIT;
        }
        if (!data.recordDelivery(deliveryId, orderId, route)) return DeliveryResult.DUPLICATE;
        if (reward > 0 && !EconomyService.creditOnce(level, claimantId, reward, deliveryId)) {
            data.forgetDelivery(deliveryId);
            return DeliveryResult.PAYOUT_LIMIT;
        }

        cargo.shrink(count);
        order.fulfil(count);
        data.changed();
        return DeliveryResult.SUCCESS;
    }

    /** Manual handoff consumes matching items across the player's inventory as one shipment. */
    public static DeliveryResult deliverInventory(ServerPlayer player, UUID orderId,
                                                  int count, UUID deliveryId) {
        if (player == null || !(player.level() instanceof ServerLevel level)
                || orderId == null || count < 1 || deliveryId == null) {
            return DeliveryResult.INVALID_REQUEST;
        }

        OrderData data = OrderData.get(level);
        if (data.hasProcessedDelivery(deliveryId)) return DeliveryResult.DUPLICATE;
        Order order = data.find(orderId);
        DeliveryResult validation = validateOrder(order, player.getUUID(), count);
        if (validation != DeliveryResult.SUCCESS) return validation;

        Item item = resolveItem(order.itemId());
        if (item == null) return DeliveryResult.WRONG_ITEM;
        Inventory inventory = player.getInventory();
        long available = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                available += stack.getCount();
                if (available >= count) break;
            }
        }
        if (available < count) return DeliveryResult.INSUFFICIENT_CARGO;

        long reward = (long) count * order.rewardPerItem();
        // Manual inventory handoffs use the same idempotent payout ledger as
        // automated routes; the delivery UUID is the durable transaction key.
        if (reward > 0 && !EconomyService.canCreditOnce(
                level, player.getUUID(), reward, deliveryId)) {
            return DeliveryResult.PAYOUT_LIMIT;
        }
        if (!data.recordDelivery(deliveryId, orderId, DeliveryRoute.MANUAL)) {
            return DeliveryResult.DUPLICATE;
        }
        if (reward > 0 && !EconomyService.creditOnce(level, player.getUUID(), reward, deliveryId)) {
            data.forgetDelivery(deliveryId);
            return DeliveryResult.PAYOUT_LIMIT;
        }

        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        inventory.setChanged();
        order.fulfil(count);
        data.changed();
        return DeliveryResult.SUCCESS;
    }

    private static DeliveryResult validateOrder(Order order, UUID claimantId, int count) {
        if (order == null || (order.status() != OrderStatus.OPEN
                && order.status() != OrderStatus.IN_PROGRESS)) return DeliveryResult.INVALID_ORDER;
        if (order.claimedBy() == null || !claimantId.equals(order.claimedBy())) {
            return DeliveryResult.WRONG_CLAIMANT;
        }
        if (count > order.remainingCount() || count > OrderService.MAX_ORDER_COUNT) {
            return DeliveryResult.INVALID_REQUEST;
        }
        return DeliveryResult.SUCCESS;
    }

    public static ResourceLocation itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static Item resolveItem(ResourceLocation itemId) {
        if (itemId == null) return null;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == null || BuiltInRegistries.ITEM.getKey(item).equals(BuiltInRegistries.ITEM.getDefaultKey())
                ? null : item;
    }
}
