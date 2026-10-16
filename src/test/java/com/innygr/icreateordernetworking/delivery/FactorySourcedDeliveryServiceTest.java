package com.innygr.icreateordernetworking.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FactorySourcedDeliveryServiceTest {
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
