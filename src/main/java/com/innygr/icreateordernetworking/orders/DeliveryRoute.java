package com.innygr.icreateordernetworking.orders;

/**
 * Delivery channels supported by the marketplace.
 *
 * Route adapters must call the shared server-side delivery service only after
 * they have verified the cargo on the logical server.
 */
public enum DeliveryRoute {
    MANUAL,
    TRAIN,
    AIRSHIP,
    FROGPORT
}
