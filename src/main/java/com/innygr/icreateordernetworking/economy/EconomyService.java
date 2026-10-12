package com.innygr.icreateordernetworking.economy;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-side API for the mod's persistent, integer-based currency ledger. */
public final class EconomyService {
    private static volatile EconomyProvider externalProvider;

    private EconomyService() {}

    /**
     * Register a verified third-party provider during server/mod initialization.
     * The first provider wins: silently switching currencies can invalidate
     * escrowed orders. Registering the same provider instance twice is harmless.
     */
    public static synchronized boolean registerProvider(EconomyProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()
                || provider.currencyName() == null || provider.currencyName().isBlank()) return false;
        if (externalProvider == null) {
            externalProvider = provider;
            return true;
        }
        return externalProvider == provider;
    }

    public static String providerId() {
        EconomyProvider provider = externalProvider;
        return provider == null ? "icreate_order_networking:internal" : provider.id();
    }

    public static String currencyName() {
        EconomyProvider provider = externalProvider;
        return provider == null ? "Order Credits" : provider.currencyName();
    }

    public static long balance(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0L;
        EconomyProvider provider = externalProvider;
        return provider == null ? EconomyData.get(level).balance(player.getUUID())
                : provider.balance(level, player.getUUID());
    }

    /** Returns whether a credit can succeed without changing the ledger. */
    public static boolean canCredit(ServerLevel level, UUID playerId, long amount) {
        if (level == null || playerId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        if (provider != null) return provider.canCredit(level, playerId, amount);
        long current = EconomyData.get(level).balance(playerId);
        return current <= EconomyData.maxBalance() - amount;
    }

    /** Credit funds only from a trusted server-side rule or administrator action. */
    public static boolean credit(ServerLevel level, UUID playerId, long amount) {
        if (level == null || playerId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        return provider == null ? EconomyData.get(level).change(playerId, amount)
                : provider.credit(level, playerId, amount);
    }

    /** Preflights a payout while allowing a matching already-recorded transaction retry. */
    public static boolean canCreditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId) {
        if (level == null || playerId == null || transactionId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        return provider == null
                ? EconomyData.get(level).canCreditOnce(transactionId, playerId, amount)
                : provider.canCreditOnce(level, playerId, amount, transactionId);
    }

    /** Credit a delivery payout exactly once across retries and world reloads. */
    public static boolean creditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId) {
        if (level == null || playerId == null || transactionId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        return provider == null
                ? EconomyData.get(level).creditOnce(transactionId, playerId, amount)
                : provider.creditOnce(level, playerId, amount, transactionId);
    }

    /** Debit funds only when the player has enough balance. */
    public static boolean debit(ServerLevel level, UUID playerId, long amount) {
        if (level == null || playerId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        return provider == null ? EconomyData.get(level).change(playerId, -amount)
                : provider.debit(level, playerId, amount);
    }

    /**
     * Transfers funds on the logical server thread. Both balances are validated
     * before either mutation; the rollback is defensive if the second mutation fails.
     */
    public static boolean transfer(ServerLevel level, UUID sender, UUID recipient, long amount) {
        if (level == null || sender == null || recipient == null || sender.equals(recipient)
                || amount <= 0) return false;

        EconomyProvider provider = externalProvider;
        if (provider != null) return provider.transfer(level, sender, recipient, amount);

        EconomyData data = EconomyData.get(level);
        if (data.balance(sender) < amount
                || data.balance(recipient) > EconomyData.maxBalance() - amount) return false;

        if (!data.change(sender, -amount)) return false;
        if (!data.change(recipient, amount)) {
            data.change(sender, amount);
            return false;
        }
        return true;
    }
}
