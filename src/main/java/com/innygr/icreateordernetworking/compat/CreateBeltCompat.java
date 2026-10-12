package com.innygr.icreateordernetworking.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry-level compatibility checks for Create belt and funnel infrastructure.
 * Detection does not itself prove that a belt transport has delivered cargo;
 * adapters must inspect real server-side transfer state and verify ownership.
 */
public final class CreateBeltCompat {
    private static final ResourceLocation BELT_CONNECTOR =
            ResourceLocation.fromNamespaceAndPath("create", "belt_connector");
    private static final ResourceLocation BELT =
            ResourceLocation.fromNamespaceAndPath("create", "belt");
    private static final ResourceLocation ANDESITE_FUNNEL =
            ResourceLocation.fromNamespaceAndPath("create", "andesite_funnel");
    private static final ResourceLocation BRASS_FUNNEL =
            ResourceLocation.fromNamespaceAndPath("create", "brass_funnel");

    private CreateBeltCompat() {}

    public static boolean isBeltInfrastructureAvailable() {
        return CreateCompat.isCreateLoaded()
                && (BuiltInRegistries.BLOCK.containsKey(BELT_CONNECTOR)
                || BuiltInRegistries.BLOCK.containsKey(BELT));
    }

    public static boolean areCreateFunnelsAvailable() {
        return CreateCompat.isCreateLoaded()
                && BuiltInRegistries.BLOCK.containsKey(ANDESITE_FUNNEL)
                && BuiltInRegistries.BLOCK.containsKey(BRASS_FUNNEL);
    }

    public static ResourceLocation beltConnectorId() { return BELT_CONNECTOR; }
    public static ResourceLocation beltId() { return BELT; }
    public static ResourceLocation andesiteFunnelId() { return ANDESITE_FUNNEL; }
    public static ResourceLocation brassFunnelId() { return BRASS_FUNNEL; }
}
