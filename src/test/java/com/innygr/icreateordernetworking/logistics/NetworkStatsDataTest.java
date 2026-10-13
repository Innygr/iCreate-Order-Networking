package com.innygr.icreateordernetworking.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

class NetworkStatsDataTest {
    @Test
    void malformedEventsAreIgnoredAndNegativeTotalsAreClamped() {
        UUID owner = UUID.randomUUID();
        CompoundTag root = new CompoundTag();

        ListTag totals = new ListTag();
        CompoundTag ownerTotals = new CompoundTag();
        ownerTotals.putString("Owner", owner.toString());
        ownerTotals.putLong("Input", -100L);
        ownerTotals.putLong("Output", 25L);
        ownerTotals.putLong("Rejected", -2L);
        totals.add(ownerTotals);
        root.put("OwnerTotals", totals);

        ListTag events = new ListTag();
        CompoundTag malformed = new CompoundTag();
        malformed.putString("Owner", owner.toString());
        malformed.putString("Dimension", "not a valid dimension!");
        malformed.putLong("Position", 0L);
        malformed.putString("Item", "minecraft:iron_ingot");
        malformed.putString("Type", NetworkStatsData.EventType.INPUT.name());
        malformed.putInt("Count", 8);
        malformed.putLong("Timestamp", 1L);
        malformed.putString("Detail", "corrupt");
        events.add(malformed);
        root.put("RecentEvents", events);

        NetworkStatsData loaded = NetworkStatsData.load(root, null);
        NetworkStatsData.Snapshot snapshot = loaded.snapshot(owner);

        assertEquals(0L, snapshot.totalInputItems());
        assertEquals(25L, snapshot.totalOutputItems());
        assertEquals(0L, snapshot.rejectedTransfers());
        assertTrue(snapshot.recentEvents().isEmpty());
    }
}
