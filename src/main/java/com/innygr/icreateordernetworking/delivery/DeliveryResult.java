package com.innygr.icreateordernetworking.delivery;

/** Outcome returned by the shared delivery entry point. */
public enum DeliveryResult {
    SUCCESS,
    INVALID_REQUEST,
    ROUTE_UNAVAILABLE,
    ROUTE_NOT_AUTHORIZED,
    NOT_IMPLEMENTED,
    ORDER_REJECTED
}
