package com.innygr.icreateordernetworking.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Safe detection for Create's built-in package Frogport.
 *
 * This deliberately uses registry IDs rather than linking against Create classes.
 * Detection is not itself a package-network integration; a route adapter still
 * needs to verify and consume the actual package contents on the server.
 */
public final class CreateFrogportCompat {
    private static final ResourceLocation PACKAGE_FROGPORT =
            ResourceLocation.fromNamespaceAndPath("create", "package_frogport");

    private CreateFrogportCompat() {}

    public static boolean isPackageFrogportAvailable() {
        return CreateCompat.isCreateLoaded() && BuiltInRegistries.ITEM.containsKey(PACKAGE_FROGPORT);
    }

    public static ResourceLocation itemId() {
        return PACKAGE_FROGPORT;
    }
}
