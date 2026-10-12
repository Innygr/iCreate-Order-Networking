package com.innygr.icreateordernetworking.delivery;

/**
 * A prepared shipment from a trusted server-side route adapter.
 *
 * prepare() must reserve (but not yet permanently consume) cargo. commit() must
 * consume that reservation exactly once and be idempotent for the request's
 * transaction ID. rollback() must release an uncommitted reservation, or restore
 * it if commit succeeded but the shared order transaction later rolled back.
 */
public interface PreparedDelivery {
    /** Permanently commits the reserved cargo; repeated calls must not double-consume. */
    boolean commit();

    /** Releases/restores the reservation. Must be safe to call more than once. */
    void rollback();
}
