package com.innygr.icreateordernetworking.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry-level compatibility checks for Create belt infrastructure.
 *
 * This class intentionally avoids hard links to Create classes so common code
 * remains load-safe. Detecting a belt connector does not by itself mean that a
 * belt can deliver an order: an adapter must still inspect the real server-side
 * belt inventory/transfer endpoint and verify cargo ownership.
 */
public final class CreateBeltCompat {
    private static final ResourceLocation BELT_CONNECTOR =
            ResourceLocation.fromNamespaceAndPath("create", "belt_connector");

    private CreateBeltCompat() {}

    public static boolean isBeltInfrastructureAvailable() {
        return CreateCompat.isCreateLoaded()
                && BuiltInRegistries.BLOCK.containsKey(BELT_CONNECTOR);
    }

    public static ResourceLocation beltConnectorId() {
        return BELT_CONNECTOR;
    }
}