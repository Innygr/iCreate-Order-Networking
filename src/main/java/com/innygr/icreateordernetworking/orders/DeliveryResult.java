package com.innygr.icreateordernetworking.orders;

/** Result returned by a delivery route when it submits cargo for an order. */
public enum DeliveryResult {
    ACCEPTED,
    DUPLICATE,
    INVALID_ORDER,
    WRONG_CLAIMANT,
    WRONG_ITEM,
    INSUFFICIENT_CARGO,
    PAYOUT_LIMIT,
    INVALID_REQUEST
}
