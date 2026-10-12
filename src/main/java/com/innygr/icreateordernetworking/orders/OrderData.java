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

/** World-persistent storage for orders. Mutate only from server-side code. */
public final class OrderData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_orders";
    private final Map<UUID, Order> orders = new LinkedHashMap<>();

    public OrderData() {}

    public static OrderData get(ServerLevel level) {
        // Orders are a server-wide marketplace, so all dimensions share the overworld data.
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
                int requested = n.getInt("Requested"), fulfilled = n.getInt("Fulfilled"), reward = n.getInt("Reward");
                UUID claimant = n.contains("ClaimedBy") ? UUID.fromString(n.getString("ClaimedBy")) : null;
                OrderStatus status = OrderStatus.valueOf(n.getString("Status"));
                if (requested > 0 && fulfilled >= 0 && fulfilled <= requested && reward >= 0) {
                    data.orders.put(id, new Order(id, owner, n.getString("OwnerName"), item,
                            requested, fulfilled, reward, claimant, status, n.getLong("CreatedAt")));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed records so a bad order does not stop the world loading.
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
        return tag;
    }

    public Collection<Order> all() { return new ArrayList<>(orders.values()); }
    public Order find(UUID id) { return orders.get(id); }
    void add(Order order) { orders.put(order.id(), order); setDirty(); }
    void changed() { setDirty(); }
}
