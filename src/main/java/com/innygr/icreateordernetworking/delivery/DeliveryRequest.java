package com.innygr.icreateordernetworking.delivery;

import java.util.UUID;
import com.innygr.icreateordernetworking.orders.OrderService;
import net.minecraft.resources.ResourceLocation;

/**
 * Transport-neutral description of a delivery attempt.
 *
 * This is request metadata, not proof that cargo arrived. Automated adapters
 * must validate their source inventory/transfer context on the server.
 */
public record DeliveryRequest(
        UUID orderId,
        ResourceLocation itemId,
        int count,
        DeliveryRoute route,
        UUID actorId,
        String sourceReference,
        UUID transactionId
) {
    public boolean isWellFormed() {
        return orderId != null && itemId != null && count > 0
                && count <= OrderService.MAX_ORDER_COUNT && route != null
                && route != DeliveryRoute.MANUAL && actorId != null && transactionId != null;
    }
}
