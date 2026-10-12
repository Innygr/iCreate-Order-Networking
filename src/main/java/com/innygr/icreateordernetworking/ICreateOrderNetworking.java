package com.innygr.icreateordernetworking;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.registry.ModBlocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;

/** Main entry point for iCreate: Order Networking. */
@Mod(ICreateOrderNetworking.MOD_ID)
public final class ICreateOrderNetworking {
    public static final String MOD_ID = "icreate_order_networking";

    public ICreateOrderNetworking() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
    }
}
