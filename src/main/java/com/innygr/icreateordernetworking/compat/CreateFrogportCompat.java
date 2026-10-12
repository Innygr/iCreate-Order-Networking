package com.innygr.icreateordernetworking.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Detection for Create's built-in package Frogport, not a separate mod. */
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