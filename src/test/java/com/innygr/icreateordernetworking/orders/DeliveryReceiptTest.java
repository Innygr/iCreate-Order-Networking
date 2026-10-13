package com.innygr.icreateordernetworking.orders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.innygr.icreateordernetworking.delivery.DeliveryRoute;

class DeliveryReceiptTest {
    private final UUID orderId = UUID.randomUUID();
    private final UUID claimantId = UUID.randomUUID();

    @Test
    void matchesOnlyTheExactTransactionDetails() {
        DeliveryReceipt receipt = new DeliveryReceipt(
                orderId, DeliveryRoute.TRAIN, claimantId, 16, false);

        assertTrue(receipt.matches(orderId, DeliveryRoute.TRAIN, claimantId, 16));
        assertFalse(receipt.matches(UUID.randomUUID(), DeliveryRoute.TRAIN, claimantId, 16));
        assertFalse(receipt.matches(orderId, DeliveryRoute.AIRSHIP, claimantId, 16));
        assertFalse(receipt.matches(orderId, DeliveryRoute.TRAIN, UUID.randomUUID(), 16));
        assertFalse(receipt.matches(orderId, DeliveryRoute.TRAIN, claimantId, 15));
        assertFalse(receipt.matches(orderId, DeliveryRoute.TRAIN, null, 16));
    }

    @Test
    void finalizationIsIdempotent() {
        DeliveryReceipt pending = new DeliveryReceipt(
                orderId, DeliveryRoute.BELT, claimantId, 4, false);

        DeliveryReceipt finalized = pending.finalized();

        assertTrue(finalized.progressApplied());
        assertTrue(finalized.matches(orderId, DeliveryRoute.BELT, claimantId, 4));
        assertSame(finalized, finalized.finalized());
    }

    @Test
    void legacyReceiptCannotAuthorizeCountedAutomatedDelivery() {
        DeliveryReceipt legacy = new DeliveryReceipt(orderId, DeliveryRoute.MANUAL);

        assertFalse(legacy.matches(orderId, DeliveryRoute.MANUAL, claimantId, 1));
        assertFalse(legacy.matches(orderId, DeliveryRoute.TRAIN, claimantId, 1));
    }

    @Test
    void rejectsInvalidReceiptCountsAndCountedManualReceipts() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryReceipt(orderId, DeliveryRoute.TRAIN, claimantId, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryReceipt(orderId, DeliveryRoute.TRAIN, claimantId,
                        OrderService.MAX_ORDER_COUNT + 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryReceipt(orderId, DeliveryRoute.MANUAL, claimantId, 1, false));
    }
}
