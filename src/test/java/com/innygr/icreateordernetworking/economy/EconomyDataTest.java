package com.innygr.icreateordernetworking.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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
    void creditReceiptsAreIndependentByPurposeScopeAndPersistAcrossReloads() {
        EconomyData original = new EconomyData();
        UUID player = UUID.randomUUID();
        UUID transaction = UUID.randomUUID();

        assertTrue(original.creditOnce(transaction, player, 100, "delivery"));
        assertTrue(original.creditOnce(transaction, player, 200, "order-cancel-refund"));
        assertEquals(300L, original.balance(player));

        EconomyData restored = EconomyData.load(original.save(new CompoundTag(), null), null);
        assertTrue(restored.creditOnce(transaction, player, 100, "delivery"));
        assertTrue(restored.creditOnce(transaction, player, 200, "order-cancel-refund"));
        assertEquals(300L, restored.balance(player));
        assertFalse(restored.creditOnce(transaction, player, 201, "order-cancel-refund"));
    }

    @Test
    void malformedCreditReceiptsAreIgnoredWithoutBreakingValidRetryReceipts() {
        EconomyData original = new EconomyData();
        UUID player = UUID.randomUUID();
        UUID transaction = UUID.randomUUID();

        assertTrue(original.creditOnce(transaction, player, 321, "delivery"));
        CompoundTag saved = original.save(new CompoundTag(), null);
        ListTag receipts = saved.getList("CreditReceipts", Tag.TAG_COMPOUND);

        CompoundTag malformedScope = new CompoundTag();
        malformedScope.putString("Transaction", UUID.randomUUID().toString());
        malformedScope.putString("Player", player.toString());
        malformedScope.putString("Scope", "");
        malformedScope.putLong("Amount", 50);
        receipts.add(malformedScope);

        CompoundTag invalidAmount = new CompoundTag();
        invalidAmount.putString("Transaction", UUID.randomUUID().toString());
        invalidAmount.putString("Player", player.toString());
        invalidAmount.putString("Scope", "delivery");
        invalidAmount.putLong("Amount", 0);
        receipts.add(invalidAmount);
        saved.put("CreditReceipts", receipts);

        EconomyData restored = EconomyData.load(saved, null);

        assertEquals(321L, restored.balance(player));
        assertTrue(restored.creditOnce(transaction, player, 321, "delivery"));
        assertFalse(restored.creditOnce(transaction, player, 322, "delivery"));
        assertEquals(321L, restored.balance(player));
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
