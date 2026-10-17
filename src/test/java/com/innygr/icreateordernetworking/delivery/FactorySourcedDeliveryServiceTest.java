package com.innygr.icreateordernetworking.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FactorySourcedDeliveryServiceTest {
    @Test
    void recoveryRequiresOriginalLoggerOwnerAndMatchingFilter() {
        java.util.UUID claimant = java.util.UUID.randomUUID();
        java.util.UUID otherOwner = java.util.UUID.randomUUID();

        assertTrue(FactorySourcedDeliveryService.mayResumeFactoryCollection(claimant, claimant, true));
        assertFalse(FactorySourcedDeliveryService.mayResumeFactoryCollection(claimant, otherOwner, true));
        assertFalse(FactorySourcedDeliveryService.mayResumeFactoryCollection(claimant, claimant, false));
        assertFalse(FactorySourcedDeliveryService.mayResumeFactoryCollection(null, claimant, true));
    }

    @Test
    void recoveryOnlyTopsUpPartialMarkedSourceCargoWhenNoPackageExists() {
        assertTrue(FactorySourcedDeliveryService.shouldResumeFactoryCollection(1, 4, false));
        assertTrue(FactorySourcedDeliveryService.shouldResumeFactoryCollection(3, 4, false));
    }

    @Test
    void recoveryNeverRefillsWhenCargoIsAbsentCompleteOrAlreadyPackaged() {
        assertFalse(FactorySourcedDeliveryService.shouldResumeFactoryCollection(0, 4, false));
        assertFalse(FactorySourcedDeliveryService.shouldResumeFactoryCollection(4, 4, false));
        assertFalse(FactorySourcedDeliveryService.shouldResumeFactoryCollection(5, 4, false));
        assertFalse(FactorySourcedDeliveryService.shouldResumeFactoryCollection(1, 4, true));
    }

    @Test
    void invalidShipmentCountsNeverResumeCollection() {
        assertFalse(FactorySourcedDeliveryService.shouldResumeFactoryCollection(1, 0, false));
        assertFalse(FactorySourcedDeliveryService.shouldResumeFactoryCollection(1, -4, false));
    }
}
