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
    private final Map<UUID, CreditReceipt> creditReceipts = new HashMap<>();

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
        ListTag receipts = tag.getList("CreditReceipts", Tag.TAG_COMPOUND);
        for (int i = 0; i < receipts.size(); i++) {
            CompoundTag receipt = receipts.getCompound(i);
            try {
                UUID transactionId = UUID.fromString(receipt.getString("Transaction"));
                UUID playerId = UUID.fromString(receipt.getString("Player"));
                long amount = receipt.getLong("Amount");
                if (amount > 0 && amount <= MAX_BALANCE) {
                    data.creditReceipts.put(transactionId, new CreditReceipt(playerId, amount));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed transaction receipts.
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

        ListTag receipts = new ListTag();
        for (Map.Entry<UUID, CreditReceipt> entry : creditReceipts.entrySet()) {
            CompoundTag saved = new CompoundTag();
            saved.putString("Transaction", entry.getKey().toString());
            saved.putString("Player", entry.getValue().playerId().toString());
            saved.putLong("Amount", entry.getValue().amount());
            receipts.add(saved);
        }
        tag.put("CreditReceipts", receipts);
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

    boolean canCreditOnce(UUID transactionId, UUID playerId, long amount) {
        if (transactionId == null || playerId == null || amount <= 0 || amount > MAX_BALANCE) return false;
        CreditReceipt existing = creditReceipts.get(transactionId);
        if (existing != null) {
            return existing.playerId().equals(playerId) && existing.amount() == amount;
        }
        long current = balance(playerId);
        return current <= MAX_BALANCE - amount;
    }

    /**
     * Idempotent credit keyed by a durable transaction ID. Replaying the same
     * transaction succeeds without paying twice; reusing its ID for different
     * parameters is rejected.
     */
    boolean creditOnce(UUID transactionId, UUID playerId, long amount) {
        if (transactionId == null || playerId == null || amount <= 0 || amount > MAX_BALANCE) return false;
        CreditReceipt existing = creditReceipts.get(transactionId);
        if (existing != null) {
            return existing.playerId().equals(playerId) && existing.amount() == amount;
        }
        if (!change(playerId, amount)) return false;
        creditReceipts.put(transactionId, new CreditReceipt(playerId, amount));
        setDirty();
        return true;
    }

    private record CreditReceipt(UUID playerId, long amount) {}

    static long maxBalance() {
        return MAX_BALANCE;
    }
}
