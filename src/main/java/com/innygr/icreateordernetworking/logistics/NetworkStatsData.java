package com.innygr.icreateordernetworking.logistics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent, bounded transfer statistics for Terminal Logger owner-networks.
 * Inventory movement is tracked separately from verified order receipts/payouts.
 * All mutation is expected on the logical server thread.
 */
public final class NetworkStatsData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_stats";
    private static final int MAX_RECENT_EVENTS = 256;
    private static final int MAX_OWNER_COUNTERS = 512;

    private final Map<UUID, OwnerTotals> totalsByOwner = new LinkedHashMap<>();
    private final Deque<TransferEvent> recentEvents = new ArrayDeque<>();

    public static NetworkStatsData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NetworkStatsData::new, NetworkStatsData::load), DATA_NAME);
    }

    public static NetworkStatsData load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkStatsData data = new NetworkStatsData();
        ListTag owners = tag.getList("OwnerTotals", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size() && data.totalsByOwner.size() < MAX_OWNER_COUNTERS; i++) {
            CompoundTag n = owners.getCompound(i);
            try {
                UUID owner = UUID.fromString(n.getString("Owner"));
                data.totalsByOwner.put(owner, new OwnerTotals(
                        Math.max(0L, n.getLong("Input")),
                        Math.max(0L, n.getLong("Output")),
                        Math.max(0L, n.getLong("Rejected"))));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed counter records.
            }
        }
        ListTag events = tag.getList("RecentEvents", Tag.TAG_COMPOUND);
        int start = Math.max(0, events.size() - MAX_RECENT_EVENTS);
        for (int i = start; i < events.size(); i++) {
            CompoundTag n = events.getCompound(i);
            try {
                data.recentEvents.addLast(new TransferEvent(
                        UUID.fromString(n.getString("Owner")),
                        ResourceLocation.parse(n.getString("Dimension")),
                        BlockPos.of(n.getLong("Position")),
                        ResourceLocation.parse(n.getString("Item")),
                        EventType.valueOf(n.getString("Type")),
                        Math.max(0, n.getInt("Count")),
                        Math.max(0L, n.getLong("Timestamp")),
                        safeDetail(n.getString("Detail"))));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed events so stats cannot prevent world loading.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        totalsByOwner.forEach((owner, totals) -> {
            CompoundTag n = new CompoundTag();
            n.putString("Owner", owner.toString());
            n.putLong("Input", totals.inputItems());
            n.putLong("Output", totals.outputItems());
            n.putLong("Rejected", totals.rejectedTransfers());
            owners.add(n);
        });
        tag.put("OwnerTotals", owners);

        ListTag events = new ListTag();
        for (TransferEvent event : recentEvents) {
            CompoundTag n = new CompoundTag();
            n.putString("Owner", event.ownerId().toString());
            n.putString("Dimension", event.dimension().toString());
            n.putLong("Position", event.position().asLong());
            n.putString("Item", event.itemId().toString());
            n.putString("Type", event.type().name());
            n.putInt("Count", event.count());
            n.putLong("Timestamp", event.timestamp());
            n.putString("Detail", event.detail());
            events.add(n);
        }
        tag.put("RecentEvents", events);
        return tag;
    }

    public void record(ServerLevel level, UUID ownerId, BlockPos position,
                       ResourceLocation itemId, EventType type, int count, String detail) {
        if (level == null || ownerId == null || position == null || itemId == null
                || type == null || count < 1) return;

        OwnerTotals totals = totalsByOwner.get(ownerId);
        if (totals == null) {
            if (totalsByOwner.size() >= MAX_OWNER_COUNTERS) {
                UUID oldest = totalsByOwner.keySet().iterator().next();
                totalsByOwner.remove(oldest);
            }
            totals = new OwnerTotals(0, 0, 0);
        }
        totals = switch (type) {
            case INPUT -> new OwnerTotals(saturatedAdd(totals.inputItems(), count),
                    totals.outputItems(), totals.rejectedTransfers());
            case OUTPUT -> new OwnerTotals(totals.inputItems(),
                    saturatedAdd(totals.outputItems(), count), totals.rejectedTransfers());
            case REJECTED -> new OwnerTotals(totals.inputItems(), totals.outputItems(),
                    saturatedAdd(totals.rejectedTransfers(), 1));
        };
        totalsByOwner.put(ownerId, totals);
        recentEvents.addLast(new TransferEvent(ownerId, level.dimension().location(),
                position.immutable(), itemId, type, count, level.getGameTime(), safeDetail(detail)));
        while (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.removeFirst();
        setDirty();
    }

    /** Read-only totals and recent events for one owner-network. */
    public Snapshot snapshot(UUID ownerId) {
        OwnerTotals totals = ownerId == null ? null : totalsByOwner.get(ownerId);
        List<TransferEvent> events = new ArrayList<>();
        if (ownerId != null) {
            for (TransferEvent event : recentEvents) {
                if (ownerId.equals(event.ownerId())) events.add(event);
            }
        }
        return new Snapshot(totals == null ? 0 : totals.inputItems(),
                totals == null ? 0 : totals.outputItems(),
                totals == null ? 0 : totals.rejectedTransfers(), List.copyOf(events));
    }

    private static long saturatedAdd(long value, int amount) {
        return value > Long.MAX_VALUE - amount ? Long.MAX_VALUE : value + amount;
    }

    private static String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) return "Transfer";
        return detail.length() > 100 ? detail.substring(0, 100) : detail;
    }

    private record OwnerTotals(long inputItems, long outputItems, long rejectedTransfers) {}
    public enum EventType { INPUT, OUTPUT, REJECTED }
    public record TransferEvent(UUID ownerId, ResourceLocation dimension, BlockPos position,
                                ResourceLocation itemId, EventType type, int count,
                                long timestamp, String detail) {}
    public record Snapshot(long totalInputItems, long totalOutputItems,
                           long rejectedTransfers, List<TransferEvent> recentEvents) {}
}
