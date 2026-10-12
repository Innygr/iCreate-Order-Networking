package com.innygr.icreateordernetworking.delivery;

/** Outcome returned by the shared delivery entry point. */
public enum DeliveryResult {
    SUCCESS,
    DUPLICATE,
    INVALID_REQUEST,
    INVALID_ORDER,
    WRONG_CLAIMANT,
    WRONG_ITEM,
    INSUFFICIENT_CARGO,
    PAYOUT_LIMIT,
    ROUTE_UNAVAILABLE,
    ROUTE_NOT_AUTHORIZED,
    NOT_IMPLEMENTED,
    ORDER_REJECTED
}
