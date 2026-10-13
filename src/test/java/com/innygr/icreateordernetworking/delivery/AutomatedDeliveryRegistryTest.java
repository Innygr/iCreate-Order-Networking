package com.innygr.icreateordernetworking.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.server.level.ServerLevel;

class AutomatedDeliveryRegistryTest {
    @AfterEach
    void clearRegistry() {
        AutomatedDeliveryRegistry.clear();
    }

    @Test
    void onlyRegistersAvailableNonDuplicateRoutes() {
        AutomatedDeliveryAdapter unavailable = adapter(DeliveryRoute.TRAIN, false);
        AutomatedDeliveryAdapter train = adapter(DeliveryRoute.TRAIN, true);
        AutomatedDeliveryAdapter duplicate = adapter(DeliveryRoute.TRAIN, true);

        assertFalse(AutomatedDeliveryRegistry.register(unavailable));
        assertTrue(AutomatedDeliveryRegistry.register(train));
        assertFalse(AutomatedDeliveryRegistry.register(duplicate));
        assertEquals(1, AutomatedDeliveryRegistry.availableRoutes().size());
        assertTrue(AutomatedDeliveryRegistry.find(DeliveryRoute.TRAIN).isPresent());
    }

    @Test
    void unregisterRequiresTheExactRegisteredInstance() {
        AutomatedDeliveryAdapter registered = adapter(DeliveryRoute.BELT, true);
        AutomatedDeliveryAdapter different = adapter(DeliveryRoute.BELT, true);

        assertTrue(AutomatedDeliveryRegistry.register(registered));
        assertFalse(AutomatedDeliveryRegistry.unregister(DeliveryRoute.BELT, different));
        assertTrue(AutomatedDeliveryRegistry.find(DeliveryRoute.BELT).isPresent());
        assertTrue(AutomatedDeliveryRegistry.unregister(DeliveryRoute.BELT, registered));
        assertTrue(AutomatedDeliveryRegistry.find(DeliveryRoute.BELT).isEmpty());
    }

    private static AutomatedDeliveryAdapter adapter(DeliveryRoute route, boolean available) {
        return new AutomatedDeliveryAdapter() {
            @Override
            public DeliveryRoute route() {
                return route;
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public boolean isAuthorized(ServerLevel level, DeliveryRequest request) {
                return false;
            }

            @Override
            public PreparedDelivery prepareCargo(ServerLevel level, DeliveryRequest request) {
                return null;
            }
        };
    }
}
