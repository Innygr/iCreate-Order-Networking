package com.innygr.icreateordernetworking.delivery;

/**
 * Result of asking a transport adapter to reserve cargo for a delivery.
 * A prepared result is not a delivered result: the caller must commit the
 * shipment through its transport and verify destination receipt separately.
 */
public record DeliveryPreparation(
        Status status,
        PreparedDelivery preparedDelivery,
        String detail) {

    public enum Status {
        PREPARED,
        INVALID_REQUEST,
        ROUTE_UNAVAILABLE,
        NOT_AUTHORIZED,
        CARGO_UNAVAILABLE
    }

    public static DeliveryPreparation failure(Status status, String detail) {
        if (status == Status.PREPARED) {
            throw new IllegalArgumentException("PREPARED requires a PreparedDelivery");
        }
        return new DeliveryPreparation(status, null, detail);
    }

    public static DeliveryPreparation prepared(PreparedDelivery delivery) {
        if (delivery == null) {
            throw new IllegalArgumentException("Prepared delivery cannot be null");
        }
        return new DeliveryPreparation(Status.PREPARED, delivery,
                "Cargo reserved. Destination receipt is still required.");
    }
}
