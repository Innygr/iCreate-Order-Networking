package com.innygr.icreateordernetworking.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry-only docking connector detection. The ID is intentionally isolated
 * here so it can be corrected to the selected Aeronautics release without
 * contaminating the order backend with optional classes.
 */
public final class AeronauticsDockingCompat {
    private static final ResourceLocation DOCKING_CONNECTOR =
            ResourceLocation.fromNamespaceAndPath("aeronautics", "docking_connector");

    private AeronauticsDockingCompat() {}

    public static boolean isDockingConnectorAvailable() {
        return CreateCompat.isAeronauticsStackAvailable()
                && BuiltInRegistries.BLOCK.containsKey(DOCKING_CONNECTOR);
    }

    public static ResourceLocation dockingConnectorId() {
        return DOCKING_CONNECTOR;
    }
}
