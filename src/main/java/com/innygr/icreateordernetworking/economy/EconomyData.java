package com.innygr.icreateordernetworking.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent balance ledger stored in the overworld's SavedData.
 * Balances are non-negative whole units; access this data on the logical server.
 */
public final class EconomyData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_economy";
    private static final long MAX_BALANCE = 9_000_000_000_000L;

    private final Map<UUID, Long> balances = new HashMap<>();

    public EconomyData() {}

    public static EconomyData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EconomyData::new, EconomyData::load), DATA_NAME);
    }

    public static EconomyData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyData data = new EconomyData();
        ListTag list = tag.getList("Balances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID playerId = UUID.fromString(entry.getString("Player"));
                long balance = entry.getLong("Amount");
                if (balance >= 0 && balance <= MAX_BALANCE) {
                    data.balances.put(playerId, balance);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed entries so they cannot prevent world loading.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            CompoundTag saved = new CompoundTag();
            saved.putString("Player", entry.getKey().toString());
            saved.putLong("Amount", entry.getValue());
            list.add(saved);
        }
        tag.put("Balances", list);
        return tag;
    }

    public long balance(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    /**
     * Changes a balance by a signed amount. Rejects underflow, overflow and values
     * above the configured cap; failure never changes the balance.
     */
    boolean change(UUID playerId, long delta) {
        if (playerId == null) return false;
        long current = balance(playerId);
        if (delta > 0 && (delta > MAX_BALANCE || current > MAX_BALANCE - delta)) return false;
        if (delta < 0 && (delta == Long.MIN_VALUE || current < -delta)) return false;
        long next = current + delta;
        if (next < 0 || next > MAX_BALANCE) return false;
        balances.put(playerId, next);
        setDirty();
        return true;
    }

    static long maxBalance() {
        return MAX_BALANCE;
    }
}
