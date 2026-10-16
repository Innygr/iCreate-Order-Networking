package com.innygr.icreateordernetworking.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry-level compatibility checks for Create's built-in package Frogport.
 *
 * The item alone is not enough to prove that the block entity implementation is
 * present: the durable adapter needs the real package-port inventory and its
 * persisted routing target. Keep this check conservative so optional Create
 * compatibility fails closed instead of registering a nonfunctional transport.
 */
public final class CreateFrogportCompat {
    private static final ResourceLocation PACKAGE_FROGPORT =
            ResourceLocation.fromNamespaceAndPath("create", "package_frogport");

    private CreateFrogportCompat() {}

    /**
     * True only when Create and all registry entries required to place and
     * inspect a Frogport are available. This is detection, not a claim that a
     * shipment has been delivered.
     */
    public static boolean isPackageFrogportAvailable() {
        return CreateCompat.isCreateLoaded()
                && BuiltInRegistries.ITEM.containsKey(PACKAGE_FROGPORT)
                && BuiltInRegistries.BLOCK.containsKey(PACKAGE_FROGPORT)
                && BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(PACKAGE_FROGPORT);
    }

    public static ResourceLocation itemId() {
        return PACKAGE_FROGPORT;
    }

    public static ResourceLocation blockId() {
        return PACKAGE_FROGPORT;
    }

    public static ResourceLocation blockEntityTypeId() {
        return PACKAGE_FROGPORT;
    }
}
