package com.innygr.icreateordernetworking.orders;

import java.util.List;
import java.util.UUID;

import com.innygr.icreateordernetworking.economy.EconomyService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative order rules.
 *
 * Rewards are escrowed when an order is created. A fulfiller is paid only after
 * the server verifies and removes the requested items from their inventory.
 * Call these methods on the logical server thread.
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

        long totalReward = (long) count * rewardPerItem;
        if (totalReward > 0 && !EconomyService.debit(level, owner.getUUID(), totalReward)) return null;

        Order order = new Order(UUID.randomUUID(), owner.getUUID(), owner.getGameProfile().getName(),
                itemId, count, 0, rewardPerItem, null, OrderStatus.OPEN, level.getGameTime(),
                OrderTeamAccess.teamKey(owner));
        OrderData.get(level).add(order);
        return order;
    }

    /** True when the owner already has a live order for this item. */
    public static boolean hasActiveOrderForOwnerItem(ServerLevel level, UUID ownerId, ResourceLocation itemId) {
        if (level == null || ownerId == null || itemId == null) return true;
        return OrderData.get(level).all().stream().anyMatch(order ->
                ownerId.equals(order.ownerId()) && itemId.equals(order.itemId())
                        && (order.status() == OrderStatus.OPEN || order.status() == OrderStatus.IN_PROGRESS));
    }

    /** Creates a no-reward automated restock order from a configured Create Redstone Requester. */
    public static Order createRestockOrder(ServerLevel level, UUID ownerId, String ownerName,
                                           String ownerTeamKey, ResourceLocation itemId, int count) {
        if (level == null || ownerId == null || itemId == null
                || !BuiltInRegistries.ITEM.containsKey(itemId)
                || count < 1 || count > MAX_ORDER_COUNT
                || hasActiveOrderForOwnerItem(level, ownerId, itemId)) return null;
        Order order = new Order(UUID.randomUUID(), ownerId,
                ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName,
                itemId, count, 0, 0, null, OrderStatus.OPEN, level.getGameTime(), ownerTeamKey);
        OrderData.get(level).add(order);
        return order;
    }

    public static List<Order> list(ServerLevel level) {
        return OrderData.get(level).all().stream()
                .filter(o -> o.status() == OrderStatus.OPEN || o.status() == OrderStatus.IN_PROGRESS)
                .toList();
    }

    /** Lists open orders visible to this player: personal orders and same-team orders. */
    public static List<Order> listFor(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return List.of();
        return list(level).stream()
                .filter(order -> OrderTeamAccess.canAccess(player, order.ownerId(), order.ownerTeamKey()))
                .toList();
    }

    public static boolean claim(ServerPlayer player, UUID orderId) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        OrderData data = OrderData.get(level);
        Order order = data.find(orderId);
        if (order == null || !OrderTeamAccess.canClaim(player, order.ownerId(), order.ownerTeamKey())
                || order.status() != OrderStatus.OPEN) return false;
        order.claim(player.getUUID());
        data.changed();
        return true;
    }

    /**
     * Verifies the claimant's inventory, consumes the requested item count,
     * advances order progress and pays the claimant from the order's escrow.
     * The operation must run on the server thread so the preflight and mutations
     * cannot race with another gameplay action.
     */
    public static boolean fulfil(ServerPlayer player, UUID orderId, int count) {
        if (!(player.level() instanceof ServerLevel level) || count < 1) return false;

        OrderData data = OrderData.get(level);
        Order order = data.find(orderId);
        if (order == null || order.status() != OrderStatus.IN_PROGRESS
                || !player.getUUID().equals(order.claimedBy())
                || count > order.remainingCount()) return false;

        Item item = BuiltInRegistries.ITEM.get(order.itemId());
        if (item == null || BuiltInRegistries.ITEM.getKey(item).equals(BuiltInRegistries.ITEM.getDefaultKey())) return false;

        long reward = (long) count * order.rewardPerItem();
        // Check the payout cap before touching inventory. Since all mutations run
        // on the server thread, a successful preflight makes the subsequent credit safe.
        if (reward > 0 && !EconomyService.canCredit(level, player.getUUID(), reward)) return false;

        Inventory inventory = player.getInventory();
        int available = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                available += stack.getCount();
                if (available >= count) break;
            }
        }
        if (available < count) return false;

        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int removed = Math.min(left, stack.getCount());
            stack.shrink(removed);
            left -= removed;
        }
        inventory.setChanged();

        if (reward > 0 && !EconomyService.credit(level, player.getUUID(), reward)) {
            // Defensive rollback: restore the exact item count if an unexpected
            // balance mutation failure occurs after preflight.
            ItemStack refund = new ItemStack(item, count);
            if (!player.getInventory().add(refund)) player.drop(refund, false);
            return false;
        }

        order.fulfil(count);
        data.changed();
        return true;
    }


    /**
     * Finalizes progress after a trusted automated route has committed its reserved
     * cargo and the shared delivery service has handled the payout/receipt.
     * This deliberately does not inspect player inventory or credit currency.
     */
    public static boolean recordAutomatedFulfilment(ServerLevel level, UUID orderId,
                                                     com.innygr.icreateordernetworking.delivery.DeliveryRoute route,
                                                     UUID claimantId, int count, UUID transactionId) {
        if (level == null || orderId == null || route == null
                || route == com.innygr.icreateordernetworking.delivery.DeliveryRoute.MANUAL
                || claimantId == null || transactionId == null
                || count < 1 || count > MAX_ORDER_COUNT) return false;
        OrderData data = OrderData.get(level);
        // Progress can only be applied to the exact durable reservation created by
        // DeliveryService. Validate all transaction fields before either normal
        // processing or the idempotent recovery path.
        DeliveryReceipt prior = data.deliveryReceipt(transactionId);
        if (prior == null || !prior.matches(orderId, route, claimantId, count)) return false;
        // A retry after a crash may find the order progress saved but the receipt
        // finalization not yet saved. Never apply the same transaction twice.
        if (data.hasAppliedAutomatedProgress(transactionId)) return true;
        Order order = data.find(orderId);
        if (order == null || (order.status() != OrderStatus.OPEN
                && order.status() != OrderStatus.IN_PROGRESS)
                || !claimantId.equals(order.claimedBy())
                || count > order.remainingCount()) return false;
        order.fulfil(count);
        data.markAutomatedProgressApplied(transactionId);
        data.changed();
        return true;
    }

    /** Kept as a compatibility guard: counts alone are never trusted as delivery. */
    @Deprecated
    public static boolean recordFulfilment(ServerPlayer player, UUID orderId, int count) {
        return false;
    }

    public static boolean cancel(ServerPlayer player, UUID orderId) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        OrderData data = OrderData.get(level);
        Order order = data.find(orderId);
        if (order == null || !order.ownerId().equals(player.getUUID())
                || (order.status() != OrderStatus.OPEN && order.status() != OrderStatus.IN_PROGRESS)
                || data.hasPendingDeliveryForOrder(orderId)) return false;

        long refund = (long) order.remainingCount() * order.rewardPerItem();
        if (refund > 0 && !EconomyService.credit(level, order.ownerId(), refund)) return false;

        order.cancel();
        data.changed();
        return true;
    }
}
