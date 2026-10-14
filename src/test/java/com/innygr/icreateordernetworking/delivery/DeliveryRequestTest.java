package com.innygr.icreateordernetworking.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.resources.ResourceLocation;

class DeliveryRequestTest {
    private final UUID orderId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final ResourceLocation itemId = ResourceLocation.parse("minecraft:iron_ingot");

    @Test
    void acceptsCompletePositiveRequest() {
        DeliveryRequest request = new DeliveryRequest(orderId, itemId, 32,
                DeliveryRoute.TRAIN, actorId, "psi:12,34,56", transactionId);

        assertTrue(request.isWellFormed());
    }

    @Test
    void rejectsMissingRequiredIdentifiers() {
        assertFalse(new DeliveryRequest(null, itemId, 1, DeliveryRoute.TRAIN,
                actorId, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, 1, DeliveryRoute.TRAIN,
                null, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, 1, DeliveryRoute.TRAIN,
                actorId, null, null).isWellFormed());
    }

    @Test
    void rejectsMissingBlankOrOversizedSourceReference() {
        assertFalse(new DeliveryRequest(orderId, itemId, 1, DeliveryRoute.TRAIN,
                actorId, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, 1, DeliveryRoute.TRAIN,
                actorId, "   ", transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, 1, DeliveryRoute.TRAIN,
                actorId, "x".repeat(257), transactionId).isWellFormed());
        assertTrue(new DeliveryRequest(orderId, itemId, 1, DeliveryRoute.TRAIN,
                actorId, "psi:12,34,56", transactionId).isWellFormed());
    }

    @Test
    void rejectsMissingItemRouteAndNonPositiveCounts() {
        assertFalse(new DeliveryRequest(orderId, null, 1, DeliveryRoute.TRAIN,
                actorId, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, 1, null,
                actorId, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, 0, DeliveryRoute.TRAIN,
                actorId, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, -1, DeliveryRoute.TRAIN,
                actorId, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, OrderService.MAX_ORDER_COUNT + 1,
                DeliveryRoute.TRAIN, actorId, null, transactionId).isWellFormed());
        assertFalse(new DeliveryRequest(orderId, itemId, 1, DeliveryRoute.MANUAL,
                actorId, null, transactionId).isWellFormed());
    }
}
