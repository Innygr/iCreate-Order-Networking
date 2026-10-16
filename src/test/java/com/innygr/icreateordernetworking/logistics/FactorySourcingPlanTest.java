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
                () -> new FactorySourcingPlan(-1, List.of(), 0, -1));
    }

    @Test
    void sourcingPlanCopiesAllocationList() {
        java.util.ArrayList<FactorySourcingPlan.Allocation> mutable = new java.util.ArrayList<>();
        FactorySourcingPlan plan = new FactorySourcingPlan(0, mutable, 0, 0);
        mutable.clear();

        assertEquals(List.of(), plan.allocations());
    }
}
