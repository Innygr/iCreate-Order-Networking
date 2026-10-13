package com.innygr.icreateordernetworking.orders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import com.innygr.icreateordernetworking.delivery.DeliveryRoute;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-persistent storage for orders and delivery idempotency receipts. */
public final class OrderData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_orders";
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final Map<UUID, DeliveryReceipt> processedDeliveries = new LinkedHashMap<>();
    /** Transaction IDs whose order progress mutation has already been applied. */
    private final Set<UUID> appliedAutomatedProgress = new LinkedHashSet<>();

    public OrderData() {}

    public static OrderData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OrderData::new, OrderData::load), DATA_NAME);
    }

    public static OrderData load(CompoundTag tag, HolderLookup.Provider registries) {
        OrderData data = new OrderData();
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag n = list.getCompound(i);
            try {
                UUID id = UUID.fromString(n.getString("Id"));
                UUID owner = UUID.fromString(n.getString("Owner"));
                ResourceLocation item = ResourceLocation.parse(n.getString("Item"));
                int requested = n.getInt("Requested");
                int fulfilled = n.getInt("Fulfilled");
                int reward = n.getInt("Reward");
                UUID claimant = n.contains("ClaimedBy") ? UUID.fromString(n.getString("ClaimedBy")) : null;
                OrderStatus status = OrderStatus.valueOf(n.getString("Status"));
                String teamKey = n.contains("OwnerTeam") ? n.getString("OwnerTeam") : null;
                if (isValidLoadedOrder(requested, fulfilled, reward, claimant, status)
                        && !data.orders.containsKey(id)) {
                    data.orders.put(id, new Order(id, owner, n.getString("OwnerName"), item,
                            requested, fulfilled, reward, claimant, status, n.getLong("CreatedAt"), teamKey));
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed records so one bad order cannot stop world loading.
            }
        }

        ListTag receipts = tag.getList("ProcessedDeliveries", Tag.TAG_COMPOUND);
        for (int i = 0; i < receipts.size(); i++) {
            CompoundTag receipt = receipts.getCompound(i);
            try {
                UUID deliveryId = UUID.fromString(receipt.getString("Id"));
                UUID orderId = receipt.contains("Order")
                        ? UUID.fromString(receipt.getString("Order")) : new UUID(0L, 0L);
                DeliveryRoute route = receipt.contains("Route")
                        ? DeliveryRoute.valueOf(receipt.getString("Route")) : DeliveryRoute.MANUAL;
                UUID claimant = receipt.contains("Claimant") ? UUID.fromString(receipt.getString("Claimant")) : null;
                int count = receipt.contains("Count") ? receipt.getInt("Count") : 0;
                boolean applied = !receipt.contains("ProgressApplied") || receipt.getBoolean("ProgressApplied");
                DeliveryReceipt loaded = new DeliveryReceipt(orderId, route, claimant, count, applied);
                data.processedDeliveries.putIfAbsent(deliveryId, loaded);
            } catch (RuntimeException ignored) {
                // Ignore malformed receipt records.
            }
        }
        ListTag appliedProgress = tag.getList("AppliedAutomatedProgress", Tag.TAG_STRING);
        for (int i = 0; i < appliedProgress.size(); i++) {
            try {
                data.appliedAutomatedProgress.add(UUID.fromString(appliedProgress.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed transaction IDs.
            }
        }
        return data;
    }

    private static boolean isValidLoadedOrder(int requested, int fulfilled, int reward,
                                              UUID claimant, OrderStatus status) {
        if (requested < 1 || requested > OrderService.MAX_ORDER_COUNT
                || fulfilled < 0 || fulfilled > requested
                || reward < 0 || reward > OrderService.MAX_REWARD_PER_ITEM || status == null) return false;
        return switch (status) {
            case OPEN -> fulfilled == 0 && claimant == null;
            case IN_PROGRESS -> fulfilled < requested && claimant != null;
            case COMPLETED -> fulfilled == requested;
            case CANCELLED -> true;
        };
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Order order : orders.values()) {
            CompoundTag n = new CompoundTag();
            n.putString("Id", order.id().toString());
            n.putString("Owner", order.ownerId().toString());
            n.putString("OwnerName", order.ownerName());
            n.putString("Item", order.itemId().toString());
            n.putInt("Requested", order.requestedCount());
            n.putInt("Fulfilled", order.fulfilledCount());
            n.putInt("Reward", order.rewardPerItem());
            if (order.claimedBy() != null) n.putString("ClaimedBy", order.claimedBy().toString());
            n.putString("Status", order.status().name());
            n.putLong("CreatedAt", order.createdAt());
            if (order.ownerTeamKey() != null) n.putString("OwnerTeam", order.ownerTeamKey());
            list.add(n);
        }
        tag.put("Orders", list);

        ListTag receipts = new ListTag();
        for (Map.Entry<UUID, DeliveryReceipt> entry : processedDeliveries.entrySet()) {
            DeliveryReceipt value = entry.getValue();
            CompoundTag saved = new CompoundTag();
            saved.putString("Id", entry.getKey().toString());
            saved.putString("Order", value.orderId().toString());
            saved.putString("Route", value.route().name());
            if (value.claimantId() != null) saved.putString("Claimant", value.claimantId().toString());
            saved.putInt("Count", value.count());
            saved.putBoolean("ProgressApplied", value.progressApplied());
            receipts.add(saved);
        }
        tag.put("ProcessedDeliveries", receipts);

        ListTag appliedProgress = new ListTag();
        for (UUID transactionId : appliedAutomatedProgress) {
            appliedProgress.add(net.minecraft.nbt.StringTag.valueOf(transactionId.toString()));
        }
        tag.put("AppliedAutomatedProgress", appliedProgress);
        return tag;
    }

    public Collection<Order> all() { return new ArrayList<>(orders.values()); }
    public Order find(UUID id) { return orders.get(id); }
    public DeliveryReceipt deliveryReceipt(UUID id) { return processedDeliveries.get(id); }
    public boolean hasProcessedDelivery(UUID id) { return processedDeliveries.containsKey(id); }

    /** Pending automated shipments must be recovered before their order can be cancelled/refunded. */
    public boolean hasPendingDeliveryForOrder(UUID orderId) {
        if (orderId == null) return false;
        return processedDeliveries.values().stream()
                .anyMatch(receipt -> orderId.equals(receipt.orderId()) && !receipt.progressApplied());
    }

    /**
     * Counts cargo reserved by unfinished automated transactions for an order.
     * A progress marker may be durable before receipt finalization; those transactions
     * must not reserve the same quantity twice during crash recovery.
     *
     * @param excludingTransactionId transaction whose own reservation the caller is processing
     */
    public long pendingDeliveryCountForOrder(UUID orderId, UUID excludingTransactionId) {
        if (orderId == null) return 0L;
        long reserved = 0L;
        for (Map.Entry<UUID, DeliveryReceipt> entry : processedDeliveries.entrySet()) {
            UUID transactionId = entry.getKey();
            DeliveryReceipt receipt = entry.getValue();
            if (!orderId.equals(receipt.orderId()) || receipt.progressApplied()
                    || receipt.count() < 1 || transactionId.equals(excludingTransactionId)
                    || appliedAutomatedProgress.contains(transactionId)) {
                continue;
            }
            if (Long.MAX_VALUE - reserved < receipt.count()) return Long.MAX_VALUE;
            reserved += receipt.count();
        }
        return reserved;
    }
    public boolean hasAppliedAutomatedProgress(UUID transactionId) {
        return appliedAutomatedProgress.contains(transactionId);
    }
    public void markAutomatedProgressApplied(UUID transactionId) {
        if (transactionId != null && appliedAutomatedProgress.add(transactionId)) setDirty();
    }

    /** Legacy/finalized receipt path for manual deliveries. */
    public boolean recordDelivery(UUID deliveryId, UUID orderId, DeliveryRoute route) {
        if (deliveryId == null || orderId == null || route != DeliveryRoute.MANUAL
                || processedDeliveries.containsKey(deliveryId)) return false;
        processedDeliveries.put(deliveryId, new DeliveryReceipt(orderId, route));
        setDirty();
        return true;
    }

    /** Records an automated transaction before cargo commit so retries can recover it safely. */
    public boolean reserveDelivery(UUID deliveryId, UUID orderId, DeliveryRoute route, UUID claimantId, int count) {
        if (deliveryId == null || orderId == null || route == null || route == DeliveryRoute.MANUAL
                || claimantId == null || count < 1 || processedDeliveries.containsKey(deliveryId)) return false;
        processedDeliveries.put(deliveryId, new DeliveryReceipt(orderId, route, claimantId, count, false));
        setDirty();
        return true;
    }

    public boolean markDeliveryProgressApplied(UUID deliveryId) {
        DeliveryReceipt receipt = processedDeliveries.get(deliveryId);
        if (receipt == null) return false;
        if (!receipt.progressApplied()) {
            processedDeliveries.put(deliveryId, receipt.finalized());
            setDirty();
        }
        return true;
    }

    public void forgetDelivery(UUID deliveryId) {
        if (processedDeliveries.remove(deliveryId) != null) setDirty();
    }

    void add(Order order) { orders.put(order.id(), order); setDirty(); }
    void changed() { setDirty(); }
}
