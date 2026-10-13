package com.innygr.icreateordernetworking.missile;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Supported missile fuels and their relative range efficiency. */
public enum MissileFuel {
    WATER(0.5D),
    LAVA(1.0D);

    private final double rangeEfficiency;

    MissileFuel(double rangeEfficiency) {
        this.rangeEfficiency = rangeEfficiency;
    }

    public double rangeEfficiency() {
        return rangeEfficiency;
    }

    /** Returns the supported fuel for a fluid, or null when it is not missile fuel. */
    public static MissileFuel fromFluid(Fluid fluid) {
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) return WATER;
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) return LAVA;
        return null;
    }

    public static int maximumRange(int tankCount, Fluid fluid) {
        MissileFuel fuel = fromFluid(fluid);
        if (fuel == null || tankCount <= 0) return 0;
        return (int) Math.floor(tankCount * 100.0D * fuel.rangeEfficiency());
    }
}
