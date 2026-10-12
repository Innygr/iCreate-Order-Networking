package com.innygr.icreateordernetworking.orders;

import java.util.UUID;

/** Durable idempotency record for a single physical delivery shipment. */
public record DeliveryReceipt(UUID orderId, DeliveryRoute route) {
    public DeliveryReceipt {
        if (orderId == null) throw new IllegalArgumentException("orderId cannot be null");
        if (route == null) throw new IllegalArgumentException("route cannot be null");
    }
}
