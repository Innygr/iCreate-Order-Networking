package com.innygr.icreateordernetworking.orders;

import java.util.UUID;
import com.innygr.icreateordernetworking.delivery.DeliveryRoute;

/** Durable idempotency record for one physical delivery transaction. */
public record DeliveryReceipt(
        UUID orderId,
        DeliveryRoute route,
        UUID claimantId,
        int count,
        boolean progressApplied) {
    public DeliveryReceipt {
        if (orderId == null) throw new IllegalArgumentException("orderId cannot be null");
        if (route == null) throw new IllegalArgumentException("route cannot be null");
        if (count < 0 || count > OrderService.MAX_ORDER_COUNT) {
            throw new IllegalArgumentException("count is outside the supported delivery range");
        }
        if (count == 0 && (route != DeliveryRoute.MANUAL || claimantId != null || !progressApplied)) {
            throw new IllegalArgumentException("only finalized manual receipts may omit transaction details");
        }
        if (count > 0 && route == DeliveryRoute.MANUAL) {
            throw new IllegalArgumentException("manual legacy receipts cannot authorize counted automated delivery");
        }
        if (count > 0 && claimantId == null) {
            throw new IllegalArgumentException("claimant required for counted delivery");
        }
    }

    /** Legacy receipts are treated as finalized because their original transaction details are unavailable. */
    public DeliveryReceipt(UUID orderId, DeliveryRoute route) {
        this(orderId, route, null, 0, true);
    }

    public boolean matches(UUID requestedOrder, DeliveryRoute requestedRoute, UUID requestedClaimant, int requestedCount) {
        return orderId.equals(requestedOrder) && route == requestedRoute
                && claimantId != null && claimantId.equals(requestedClaimant) && count == requestedCount;
    }

    public DeliveryReceipt finalized() {
        return progressApplied ? this : new DeliveryReceipt(orderId, route, claimantId, count, true);
    }
}
