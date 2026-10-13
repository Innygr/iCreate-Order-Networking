package com.innygr.icreateordernetworking.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.server.level.ServerLevel;

class EconomyServiceProviderTest {
    @Test
    void rejectsProviderWhoseMetadataThrowsInsteadOfCrashingInitialization() {
        assertFalse(EconomyService.registerProvider(provider(true, false)));
        assertFalse(EconomyService.registerProvider(provider(false, true)));
    }

    private static EconomyProvider provider(boolean failId, boolean failCurrencyName) {
        return new EconomyProvider() {
            @Override
            public String id() {
                if (failId) throw new LinkageError("optional economy API is missing");
                return "test:currency";
            }

            @Override
            public String currencyName() {
                if (failCurrencyName) throw new IllegalStateException("provider not initialized");
                return "Test Credits";
            }

            @Override public long balance(ServerLevel level, UUID playerId) { return 0; }
            @Override public boolean canCredit(ServerLevel level, UUID playerId, long amount) { return true; }
            @Override public boolean credit(ServerLevel level, UUID playerId, long amount) { return true; }
            @Override public boolean canCreditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId) { return true; }
            @Override public boolean creditOnce(ServerLevel level, UUID playerId, long amount, UUID transactionId) { return true; }
            @Override public boolean debit(ServerLevel level, UUID playerId, long amount) { return true; }
            @Override public boolean transfer(ServerLevel level, UUID sender, UUID recipient, long amount) { return true; }
        };
    }
}
