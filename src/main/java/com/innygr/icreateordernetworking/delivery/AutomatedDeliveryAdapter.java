package com.innygr.icreateordernetworking.delivery;

import net.minecraft.server.level.ServerLevel;

/**
 * Server-side bridge for train, airship, and Frogport cargo systems.
 * Implementations are only registered when the owning mod and API are verified.
 * Client-supplied source references are never proof that cargo arrived.
 */
public interface AutomatedDeliveryAdapter {
    DeliveryRoute route();

    /** Whether the required mod stack and adapter API are available. */
    boolean isAvailable();

    /** Validates the route/source context on the logical server thread. */
    boolean isAuthorized(ServerLevel level, DeliveryRequest request);

    /**
     * Reserves the requested cargo without permanently consuming it.
     * Return null if cargo is absent, stale, or cannot be reserved safely. A null
     * return must guarantee that no transaction-marked cargo remains at the source;
     * if preparation is interrupted after partial mutation, throw instead so the
     * caller retains the durable RESERVED journal entry for recovery.
     */
    PreparedDelivery prepareCargo(ServerLevel level, DeliveryRequest request);

    /**
     * Rebuilds an idempotent handle for a persisted RESERVED shipment after restart.
     * Implementations must locate the existing reservation, never reserve new cargo.
     */
    default PreparedDelivery resumeCargo(ServerLevel level, ShipmentJournalData.Shipment shipment) {
        return null;
    }

    /**
     * Independently verifies that the exact transaction's cargo has arrived at
     * the named destination. Implementations must inspect server-owned endpoint
     * state; a caller-provided string alone is never evidence of receipt.
     */
    default boolean verifyDestinationReceipt(ServerLevel level,
            ShipmentJournalData.Shipment shipment, String destinationReference) {
        return false;
    }
}
