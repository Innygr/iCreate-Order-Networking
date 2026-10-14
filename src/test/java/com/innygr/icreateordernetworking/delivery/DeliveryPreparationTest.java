package com.innygr.icreateordernetworking.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DeliveryPreparationTest {
    @Test
    void validationFailuresNeverExposeAPreparedCargoReservation() {
        for (DeliveryPreparation.Status status : DeliveryPreparation.Status.values()) {
            if (status == DeliveryPreparation.Status.PREPARED) continue;
            DeliveryPreparation result = DeliveryPreparation.failure(status, "rejected");
            assertEquals(status, result.status());
            assertNull(result.preparedDelivery());
        }
    }

    @Test
    void preparedResultRequiresAndCarriesReservation() {
        PreparedDelivery reservation = new PreparedDelivery() {
            @Override public boolean commit() { return true; }
            @Override public void rollback() {}
        };

        DeliveryPreparation result = DeliveryPreparation.prepared(reservation);

        assertEquals(DeliveryPreparation.Status.PREPARED, result.status());
        assertNotNull(result.preparedDelivery());
    }

    @Test
    void preparedStatusCannotBeConstructedAsFailure() {
        assertThrows(IllegalArgumentException.class,
                () -> DeliveryPreparation.failure(DeliveryPreparation.Status.PREPARED, "invalid"));
    }

    @Test
    void nullReservationCannotBeMarkedPrepared() {
        assertThrows(IllegalArgumentException.class, () -> DeliveryPreparation.prepared(null));
    }
}
