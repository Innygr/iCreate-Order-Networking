package com.innygr.icreateordernetworking.delivery;

import net.minecraft.server.level.ServerLevel;

/**
 * Server-side bridge for train, airship, and Frogport cargo systems.
 *
 * Implementations are registered only when the owning mod and its verified API
 * are present. The adapter must validate the actual source cargo and authorize
 * the route before it may remove any items. Client-provided source references
 * are never sufficient proof of delivery.
 */
public interface AutomatedDeliveryAdapter {
    DeliveryRoute route();

    /** Whether the required mod stack and adapter API are available. */
    boolean isAvailable();

    /** Validates the route/source context on the logical server thread. */
    boolean isAuthorized(ServerLevel level, DeliveryRequest request);

    /**
     * Transfers/removes the requested cargo from its real source.
     * Must be server-thread-only and must not grant rewards or mutate order state.
     */
    boolean consumeCargo(ServerLevel level, DeliveryRequest request);
}
