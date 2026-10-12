package com.innygr.icreateordernetworking.orders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
                if (requested > 0 && fulfilled >= 0 && fulfilled <= requested && reward >= 0) {
                    data.orders.put(id, new Order(id, owner, n.getString("OwnerName"), item,
                            requested, fulfilled, reward, claimant, status, n.getLong("CreatedAt")));
                }
            } catch (IllegalArgumentException ignored) {
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
                data.processedDeliveries.put(deliveryId, new DeliveryReceipt(orderId, route));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed receipt records.
            }
        }
        return data;
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
            list.add(n);
        }
        tag.put("Orders", list);

        ListTag receipts = new ListTag();
        for (Map.Entry<UUID, DeliveryReceipt> entry : processedDeliveries.entrySet()) {
            CompoundTag receipt = new CompoundTag();
            receipt.putString("Id", entry.getKey().toString());
            receipt.putString("Order", entry.getValue().orderId().toString());
            receipt.putString("Route", entry.getValue().route().name());
            receipts.add(receipt);
        }
        tag.put("ProcessedDeliveries", receipts);
        return tag;
    }

    public Collection<Order> all() { return new ArrayList<>(orders.values()); }
    public Order find(UUID id) { return orders.get(id); }
    public boolean hasProcessedDelivery(UUID deliveryId) {
        return processedDeliveries.containsKey(deliveryId);
    }

    boolean recordDelivery(UUID deliveryId, UUID orderId, DeliveryRoute route) {
        if (deliveryId == null || orderId == null || route == null
                || processedDeliveries.containsKey(deliveryId)) return false;
        processedDeliveries.put(deliveryId, new DeliveryReceipt(orderId, route));
        setDirty();
        return true;
    }

    void forgetDelivery(UUID deliveryId) {
        if (processedDeliveries.remove(deliveryId) != null) setDirty();
    }

    void add(Order order) { orders.put(order.id(), order); setDirty(); }
    void changed() { setDirty(); }
}
