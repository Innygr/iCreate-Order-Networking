package com.innygr.icreateordernetworking.orders;

import java.util.UUID;

import com.innygr.icreateordernetworking.economy.EconomyService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shared server-side entry point for every delivery method.
 *
 * IMPORTANT: call only from a trusted server-side route adapter after it has
 * obtained the real mutable cargo stack from the route's inventory/package.
 * Never expose this method directly to a client packet or accept client-supplied
 * "delivery completed" claims.
 *
 * All calls must run on the logical server thread.
 */
public final class DeliveryService {
    private DeliveryService() {}

    /**
     * Submits real cargo from a server-owned source. A unique deliveryId makes
     * transport retries idempotent; it must remain the same for retries of the
     * same physical shipment, and be new for a distinct shipment.
     *
     * The route adapter is responsible for proving the source is a valid
     * delivery endpoint. This method validates order ownership/claim, item,
     * quantity, payout capacity, consumes the cargo, and records progress.
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
        if (order == null || (order.status() != OrderStatus.OPEN
                && order.status() != OrderStatus.IN_PROGRESS)) {
            return DeliveryResult.INVALID_ORDER;
        }
        if (order.claimedBy() == null || !claimantId.equals(order.claimedBy())) {
            return DeliveryResult.WRONG_CLAIMANT;
        }
        if (count > order.remainingCount()) return DeliveryResult.INVALID_REQUEST;
        if (cargo.getCount() < count) return DeliveryResult.INSUFFICIENT_CARGO;

        Item item = BuiltInRegistries.ITEM.get(order.itemId());
        if (item == null || BuiltInRegistries.ITEM.getKey(item).equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return DeliveryResult.WRONG_ITEM;
        }
        if (!cargo.is(item)) return DeliveryResult.WRONG_ITEM;

        long reward = (long) count * order.rewardPerItem();
        if (reward > 0 && !EconomyService.canCredit(level, claimantId, reward)) {
            return DeliveryResult.PAYOUT_LIMIT;
        }

        // All validation and payout preflight happen before mutating cargo.
        // Mark the receipt and order in the same server-thread operation to
        // prevent duplicate route callbacks from paying twice.
        data.recordDelivery(deliveryId, orderId, route);
        cargo.shrink(count);
        order.fulfil(count);
        data.changed();

        if (reward > 0 && !EconomyService.credit(level, claimantId, reward)) {
            // This should be unreachable after canCredit on the same server
            // thread. We fail closed rather than retrying the receipt and
            // risking a duplicate payment. Logically, the order remains
            // fulfilled; this path should be covered by tests.
            return DeliveryResult.PAYOUT_LIMIT;
        }

        return DeliveryResult.ACCEPTED;
    }

    /** Resolves an item's registry ID for adapters and diagnostics. */
    public static ResourceLocation itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static Item resolveItem(ResourceLocation itemId) {
        if (itemId == null) return null;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == null || BuiltInRegistries.ITEM.getKey(item).equals(BuiltInRegistries.ITEM.getDefaultKey()) ? null : item;
    }
}
