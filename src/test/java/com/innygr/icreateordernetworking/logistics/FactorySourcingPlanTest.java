package com.innygr.icreateordernetworking.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FactorySourcingPlanTest {
    @Test
    void emptyOrderIsFullySourcedWithoutAllocations() {
        FactorySourcingPlan plan = new FactorySourcingPlan(0, List.of(), 0, 0);

        assertTrue(plan.fullySourcedLocally());
        assertEquals(0, plan.requestedCount());
        assertEquals(List.of(), plan.allocations());
    }

    @Test
    void sourcingPlanRejectsInconsistentQuantities() {
        assertThrows(IllegalArgumentException.class,
                () -> new FactorySourcingPlan(10, List.of(), 2, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new FactorySourcingPlan(10, List.of(), 2, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new FactorySourcingPlan(-1, List.of(), 0, -1));
    }

    @Test
    void allocationCannotExceedObservedStockOrUseNonPositiveCounts() {
        FactoryEndpointRegistry.FactoryEndpoint endpoint = new FactoryEndpointRegistry.FactoryEndpoint() {
            @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() {
                return net.minecraft.world.level.Level.OVERWORLD;
            }
            @Override public net.minecraft.core.BlockPos position() {
                return net.minecraft.core.BlockPos.ZERO;
            }
            @Override public java.util.UUID networkId() {
                return new java.util.UUID(0L, 1L);
            }
            @Override public int availableCount(net.minecraft.resources.ResourceLocation itemId) {
                return 8;
            }
        };
        FactoryEndpointRegistry.StockOffer offer = new FactoryEndpointRegistry.StockOffer(
                net.minecraft.core.BlockPos.ZERO,
                net.minecraft.resources.ResourceLocation.parse("minecraft:iron_ingot"),
                8, 0.0, endpoint);

        assertThrows(IllegalArgumentException.class,
                () -> new FactorySourcingPlan.Allocation(offer, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new FactorySourcingPlan.Allocation(offer, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new FactorySourcingPlan.Allocation(offer, 9));
        assertEquals(8, new FactorySourcingPlan.Allocation(offer, 8).count());
    }

    @Test
    void sourcingPlanCopiesAllocationList() {
        java.util.ArrayList<FactorySourcingPlan.Allocation> mutable = new java.util.ArrayList<>();
        FactorySourcingPlan plan = new FactorySourcingPlan(0, mutable, 0, 0);
        mutable.clear();

        assertEquals(List.of(), plan.allocations());
    }
}
