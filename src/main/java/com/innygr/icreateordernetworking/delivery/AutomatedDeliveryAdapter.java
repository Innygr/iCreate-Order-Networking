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
     * Return null if cargo is absent, stale, or cannot be reserved.
     */
    PreparedDelivery prepareCargo(ServerLevel level, DeliveryRequest request);
}
