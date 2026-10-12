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

    boolean creditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId);

    boolean debit(ServerLevel level, UUID playerId, long amount);

    boolean transfer(ServerLevel level, UUID sender, UUID recipient, long amount);
}
