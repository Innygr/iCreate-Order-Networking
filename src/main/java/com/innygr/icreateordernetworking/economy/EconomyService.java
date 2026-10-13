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
        if (provider == null) return false;
        try {
            if (provider.id() == null || provider.id().isBlank()
                    || provider.currencyName() == null || provider.currencyName().isBlank()) return false;
        } catch (RuntimeException | LinkageError exception) {
            // A broken optional provider must not crash mod initialization.
            return false;
        }
        if (externalProvider == null) {
            externalProvider = provider;
            return true;
        }
        return externalProvider == provider;
    }

    public static String providerId() {
        EconomyProvider provider = externalProvider;
        if (provider == null) return "icreate_order_networking:internal";
        try {
            return provider.id();
        } catch (RuntimeException | LinkageError exception) {
            return "unavailable";
        }
    }

    public static String currencyName() {
        EconomyProvider provider = externalProvider;
        if (provider == null) return "Order Credits";
        try {
            return provider.currencyName();
        } catch (RuntimeException | LinkageError exception) {
            return "Unavailable currency";
        }
    }

    public static long balance(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0L;
        EconomyProvider provider = externalProvider;
        if (provider == null) return EconomyData.get(level).balance(player.getUUID());
        try {
            return Math.max(0L, provider.balance(level, player.getUUID()));
        } catch (RuntimeException | LinkageError exception) {
            return 0L;
        }
    }

    /** Returns whether a credit can succeed without changing the ledger. */
    public static boolean canCredit(ServerLevel level, UUID playerId, long amount) {
        if (level == null || playerId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        if (provider != null) {
            try {
                return provider.canCredit(level, playerId, amount);
            } catch (RuntimeException | LinkageError exception) {
                return false;
            }
        }
        long current = EconomyData.get(level).balance(playerId);
        return current <= EconomyData.maxBalance() - amount;
    }

    /** Credit funds only from a trusted server-side rule or administrator action. */
    public static boolean credit(ServerLevel level, UUID playerId, long amount) {
        if (level == null || playerId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        if (provider == null) return EconomyData.get(level).change(playerId, amount);
        try {
            return provider.credit(level, playerId, amount);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    /** Preflights a payout while allowing a matching already-recorded transaction retry. */
    public static boolean canCreditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId) {
        return canCreditOnce(level, playerId, amount, transactionId, "delivery");
    }

    /** Preflights an idempotent credit within a purpose-specific transaction scope. */
    public static boolean canCreditOnce(ServerLevel level, UUID playerId, long amount,
                                        UUID transactionId, String scope) {
        if (level == null || playerId == null || transactionId == null || amount <= 0
                || scope == null || scope.isBlank() || scope.length() > 64) return false;
        EconomyProvider provider = externalProvider;
        if (provider == null) return EconomyData.get(level).canCreditOnce(transactionId, playerId, amount, scope);
        try {
            return provider.canCreditOnce(level, playerId, amount, transactionId, scope);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    /** Credit a delivery payout exactly once across retries and world reloads. */
    public static boolean creditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId) {
        return creditOnce(level, playerId, amount, transactionId, "delivery");
    }

    /** Credits once within a purpose-specific transaction scope. */
    public static boolean creditOnce(ServerLevel level, UUID playerId, long amount,
                                     UUID transactionId, String scope) {
        if (level == null || playerId == null || transactionId == null || amount <= 0
                || scope == null || scope.isBlank() || scope.length() > 64) return false;
        EconomyProvider provider = externalProvider;
        if (provider == null) return EconomyData.get(level).creditOnce(transactionId, playerId, amount, scope);
        try {
            return provider.creditOnce(level, playerId, amount, transactionId, scope);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    /** Debit funds only when the player has enough balance. */
    public static boolean debit(ServerLevel level, UUID playerId, long amount) {
        if (level == null || playerId == null || amount <= 0) return false;
        EconomyProvider provider = externalProvider;
        if (provider == null) return EconomyData.get(level).change(playerId, -amount);
        try {
            return provider.debit(level, playerId, amount);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    /**
     * Transfers funds on the logical server thread. Both balances are validated
     * before either mutation; the rollback is defensive if the second mutation fails.
     */
    public static boolean transfer(ServerLevel level, UUID sender, UUID recipient, long amount) {
        if (level == null || sender == null || recipient == null || sender.equals(recipient)
                || amount <= 0) return false;

        EconomyProvider provider = externalProvider;
        if (provider != null) {
            try {
                return provider.transfer(level, sender, recipient, amount);
            } catch (RuntimeException | LinkageError exception) {
                return false;
            }
        }

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
