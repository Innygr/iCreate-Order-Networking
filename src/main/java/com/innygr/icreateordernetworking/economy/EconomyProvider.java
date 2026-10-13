package com.innygr.icreateordernetworking.economy;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-side integration point for third-party economy mods.
 *
 * Providers are registered by an explicit compatibility adapter after their
 * external API has been verified. Amounts are whole currency units. A provider
 * must use a durable transaction key for creditOnce; returning true means the
 * same transaction will never credit the same account twice, including after
 * a server restart.
 */
public interface EconomyProvider {
    /** Stable identifier for logs/configuration, e.g. "modid:currency". */
    String id();

    /** Human-readable currency name for future UI display. */
    String currencyName();

    long balance(ServerLevel level, UUID playerId);

    boolean canCredit(ServerLevel level, UUID playerId, long amount);

    boolean credit(ServerLevel level, UUID playerId, long amount);

    boolean canCreditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId);

    boolean creditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId);

    /**
     * Purpose-scoped idempotency extension. Providers with composite transaction
     * keys should override these methods; the default preserves compatibility by
     * deriving a deterministic key from the scope and transaction ID.
     */
    default boolean canCreditOnce(ServerLevel level, UUID playerId, long amount,
                                  UUID transactionId, String scope) {
        return canCreditOnce(level, playerId, amount, scopedTransactionId(transactionId, scope));
    }

    default boolean creditOnce(ServerLevel level, UUID playerId, long amount,
                               UUID transactionId, String scope) {
        return creditOnce(level, playerId, amount, scopedTransactionId(transactionId, scope));
    }

    private static UUID scopedTransactionId(UUID transactionId, String scope) {
        if (transactionId == null || scope == null || scope.isBlank() || scope.length() > 64) {
            throw new IllegalArgumentException("Invalid scoped transaction key");
        }
        return UUID.nameUUIDFromBytes((scope + ":" + transactionId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    boolean debit(ServerLevel level, UUID playerId, long amount);

    boolean transfer(ServerLevel level, UUID sender, UUID recipient, long amount);
}
