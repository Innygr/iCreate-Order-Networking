package com.innygr.icreateordernetworking.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Detection for Create train transfer hardware. Detection alone does not prove
 * a train cargo handoff; a server-side adapter must verify the PSI transfer.
 */
public final class CreateTrainCompat {
    private static final ResourceLocation PORTABLE_STORAGE_INTERFACE =
            ResourceLocation.fromNamespaceAndPath("create", "portable_storage_interface");

    private CreateTrainCompat() {}

    public static boolean isPortableStorageInterfaceAvailable() {
        return CreateCompat.isCreateLoaded()
                && BuiltInRegistries.BLOCK.containsKey(PORTABLE_STORAGE_INTERFACE);
    }

    public static ResourceLocation portableStorageInterfaceId() {
        return PORTABLE_STORAGE_INTERFACE;
    }
}
