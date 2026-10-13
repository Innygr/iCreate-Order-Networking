package com.innygr.icreateordernetworking.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

class EconomyDataTest {
    @Test
    void creditOnceIsIdempotentAndRejectsTransactionReuse() {
        EconomyData data = new EconomyData();
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        UUID transaction = UUID.randomUUID();

        assertTrue(data.creditOnce(transaction, player, 75));
        assertEquals(75, data.balance(player));
        assertTrue(data.creditOnce(transaction, player, 75));
        assertEquals(75, data.balance(player));
        assertFalse(data.creditOnce(transaction, player, 76));
        assertFalse(data.creditOnce(transaction, otherPlayer, 75));
        assertEquals(75, data.balance(player));
        assertEquals(0, data.balance(otherPlayer));
    }

    @Test
    void balancesAndCreditReceiptsSurviveSavedDataRoundTrip() {
        EconomyData original = new EconomyData();
        UUID player = UUID.randomUUID();
        UUID transaction = UUID.randomUUID();

        assertTrue(original.creditOnce(transaction, player, 1234));
        CompoundTag saved = original.save(new CompoundTag(), null);
        EconomyData restored = EconomyData.load(saved, null);

        assertEquals(1234, restored.balance(player));
        assertTrue(restored.creditOnce(transaction, player, 1234));
        assertEquals(1234, restored.balance(player));
        assertFalse(restored.creditOnce(transaction, player, 1235));
    }

    @Test
    void balanceMutationRejectsUnderflowAndOverflowWithoutChangingBalance() {
        EconomyData data = new EconomyData();
        UUID player = UUID.randomUUID();

        assertFalse(data.change(player, -1));
        assertEquals(0, data.balance(player));
        assertTrue(data.change(player, 500));
        assertFalse(data.change(player, Long.MAX_VALUE));
        assertEquals(500, data.balance(player));
        assertFalse(data.change(player, Long.MIN_VALUE));
        assertEquals(500, data.balance(player));
    }
}
