package com.innygr.icreateordernetworking.logistics;

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
 * Persistent, bounded transfer statistics grouped by owner, factory, and FTB party.
 * Inventory movement is distinct from verified order receipts/payouts. All mutation
 * is expected on the logical server thread.
 */
public final class NetworkStatsData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_stats";
    private static final int MAX_RECENT_EVENTS = 256;
    private static final int MAX_OWNER_COUNTERS = 512;
    private static final int MAX_FACTORY_COUNTERS = 1024;
    private static final int MAX_TEAM_COUNTERS = 512;

    private final Map<UUID, Totals> totalsByOwner = new LinkedHashMap<>();
    private final Map<UUID, Totals> totalsByFactory = new LinkedHashMap<>();
    private final Map<UUID, Totals> totalsByTeam = new LinkedHashMap<>();
    private final Deque<TransferEvent> recentEvents = new ArrayDeque<>();

    public static NetworkStatsData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NetworkStatsData::new, NetworkStatsData::load), DATA_NAME);
    }

    public static NetworkStatsData load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkStatsData data = new NetworkStatsData();
        loadTotals(tag.getList("OwnerTotals", Tag.TAG_COMPOUND), data.totalsByOwner, MAX_OWNER_COUNTERS, "Owner");
        loadTotals(tag.getList("FactoryTotals", Tag.TAG_COMPOUND), data.totalsByFactory, MAX_FACTORY_COUNTERS, "Factory");
        loadTotals(tag.getList("TeamTotals", Tag.TAG_COMPOUND), data.totalsByTeam, MAX_TEAM_COUNTERS, "Team");

        ListTag events = tag.getList("RecentEvents", Tag.TAG_COMPOUND);
        int start = Math.max(0, events.size() - MAX_RECENT_EVENTS);
        for (int i = start; i < events.size(); i++) {
            CompoundTag n = events.getCompound(i);
            try {
                data.recentEvents.addLast(new TransferEvent(
                        UUID.fromString(n.getString("Owner")),
                        readUuid(n, "Factory"), readUuid(n, "Team"),
                        ResourceLocation.parse(n.getString("Dimension")),
                        BlockPos.of(n.getLong("Position")),
                        ResourceLocation.parse(n.getString("Item")),
                        EventType.valueOf(n.getString("Type")),
                        Math.max(0, n.getInt("Count")),
                        Math.max(0L, n.getLong("Timestamp")),
                        safeDetail(n.getString("Detail"))));
            } catch (RuntimeException ignored) {
                // Ignore malformed events/resource IDs so stats cannot prevent world loading.
            }
        }
        return data;
    }

    private static void loadTotals(ListTag list, Map<UUID, Totals> target, int cap, String idKey) {
        for (int i = 0; i < list.size() && target.size() < cap; i++) {
            CompoundTag n = list.getCompound(i);
            try {
                target.put(UUID.fromString(n.getString(idKey)), new Totals(
                        Math.max(0L, n.getLong("Input")),
                        Math.max(0L, n.getLong("Output")),
                        Math.max(0L, n.getLong("Rejected"))));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed counter records.
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        saveTotals(tag, "OwnerTotals", totalsByOwner, "Owner");
        saveTotals(tag, "FactoryTotals", totalsByFactory, "Factory");
        saveTotals(tag, "TeamTotals", totalsByTeam, "Team");

        ListTag events = new ListTag();
        for (TransferEvent event : recentEvents) {
            CompoundTag n = new CompoundTag();
            n.putString("Owner", event.ownerId().toString());
            putUuid(n, "Factory", event.factoryId());
            putUuid(n, "Team", event.teamId());
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

    private static void saveTotals(CompoundTag tag, String listKey, Map<UUID, Totals> source, String idKey) {
        ListTag list = new ListTag();
        source.forEach((id, totals) -> {
            CompoundTag n = new CompoundTag();
            n.putString(idKey, id.toString());
            n.putLong("Input", totals.inputItems());
            n.putLong("Output", totals.outputItems());
            n.putLong("Rejected", totals.rejectedTransfers());
            list.add(n);
        });
        tag.put(listKey, list);
    }

    /** Backwards-compatible owner-only record method for older integrations. */
    public void record(ServerLevel level, UUID ownerId, BlockPos position,
                       ResourceLocation itemId, EventType type, int count, String detail) {
        record(level, ownerId, null, null, position, itemId, type, count, detail);
    }

    /** Record one observed movement against owner totals and, when known, factory/team totals. */
    public void record(ServerLevel level, UUID ownerId, UUID factoryId, UUID teamId,
                       BlockPos position, ResourceLocation itemId, EventType type, int count, String detail) {
        if (level == null || ownerId == null || position == null || itemId == null
                || type == null || count < 1) return;

        updateTotals(totalsByOwner, ownerId, MAX_OWNER_COUNTERS, type, count);
        if (factoryId != null) updateTotals(totalsByFactory, factoryId, MAX_FACTORY_COUNTERS, type, count);
        if (teamId != null) updateTotals(totalsByTeam, teamId, MAX_TEAM_COUNTERS, type, count);

        recentEvents.addLast(new TransferEvent(ownerId, factoryId, teamId, level.dimension().location(),
                position.immutable(), itemId, type, count, level.getGameTime(), safeDetail(detail)));
        while (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.removeFirst();
        setDirty();
    }

    private static void updateTotals(Map<UUID, Totals> map, UUID id, int cap, EventType type, int count) {
        Totals totals = map.get(id);
        if (totals == null) {
            if (map.size() >= cap) map.remove(map.keySet().iterator().next());
            totals = new Totals(0, 0, 0);
        }
        totals = switch (type) {
            case INPUT -> new Totals(saturatedAdd(totals.inputItems(), count),
                    totals.outputItems(), totals.rejectedTransfers());
            case OUTPUT -> new Totals(totals.inputItems(),
                    saturatedAdd(totals.outputItems(), count), totals.rejectedTransfers());
            case REJECTED -> new Totals(totals.inputItems(), totals.outputItems(),
                    saturatedAdd(totals.rejectedTransfers(), 1));
        };
        map.put(id, totals);
    }

    public Snapshot snapshot(UUID ownerId) {
        return snapshotMatching(ownerId, null, null);
    }

    public Snapshot snapshotFactory(UUID factoryId) {
        return snapshotMatching(null, factoryId, null);
    }

    public Snapshot snapshotTeam(UUID teamId) {
        return snapshotMatching(null, null, teamId);
    }

    private Snapshot snapshotMatching(UUID ownerId, UUID factoryId, UUID teamId) {
        Totals totals = ownerId != null ? totalsByOwner.get(ownerId)
                : factoryId != null ? totalsByFactory.get(factoryId)
                : teamId != null ? totalsByTeam.get(teamId) : null;
        List<TransferEvent> events = new ArrayList<>();
        for (TransferEvent event : recentEvents) {
            boolean matches = ownerId != null ? ownerId.equals(event.ownerId())
                    : factoryId != null ? factoryId.equals(event.factoryId())
                    : teamId != null && teamId.equals(event.teamId());
            if (matches) events.add(event);
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

    private static void putUuid(CompoundTag tag, String key, UUID value) {
        if (value != null) tag.putString(key, value.toString());
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        return UUID.fromString(tag.getString(key));
    }

    private record Totals(long inputItems, long outputItems, long rejectedTransfers) {}
    public enum EventType { INPUT, OUTPUT, REJECTED }
    public record TransferEvent(UUID ownerId, UUID factoryId, UUID teamId,
                                ResourceLocation dimension, BlockPos position,
                                ResourceLocation itemId, EventType type, int count,
                                long timestamp, String detail) {}
    public record Snapshot(long totalInputItems, long totalOutputItems,
                           long rejectedTransfers, List<TransferEvent> recentEvents) {}
}
