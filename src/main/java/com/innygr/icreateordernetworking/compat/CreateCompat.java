package com.innygr.icreateordernetworking.compat;

import net.neoforged.fml.ModList;

/**
 * Optional-mod detection for Create-family integrations.
 *
 * This class intentionally does not import classes from Create, Aeronautics, or
 * Sable. That keeps class loading safe when optional integrations are absent and
 * avoids binding the core order/economy backend to Aeronautics implementation
 * details that may change between releases.
 */
public final class CreateCompat {
    public static final String CREATE_MOD_ID = "create";
    public static final String AERONAUTICS_MOD_ID = "aeronautics";
    public static final String SABLE_MOD_ID = "sable";

    private CreateCompat() {}

    public static boolean isCreateLoaded() {
        return isLoaded(CREATE_MOD_ID);
    }

    public static boolean isAeronauticsLoaded() {
        return isLoaded(AERONAUTICS_MOD_ID);
    }

    public static boolean isSableLoaded() {
        return isLoaded(SABLE_MOD_ID);
    }

    /**
     * True when the complete Create: Aeronautics dependency stack is present.
     * Aeronautics itself requires both Create and Sable.
     */
    public static boolean isAeronauticsStackAvailable() {
        return isCreateLoaded() && isSableLoaded() && isAeronauticsLoaded();
    }

    /**
     * A Create-family environment is available for optional integrations.
     * The base mod still uses vanilla item IDs and server-side order services,
     * so order persistence and fulfilment do not depend on a moving contraption.
     */
    public static boolean isCreateEnvironmentAvailable() {
        return isCreateLoaded();
    }

    private static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
