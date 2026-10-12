package com.innygr.icreateordernetworking;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.registry.ModBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Main entry point for iCreate: Order Networking. */
@Mod(ICreateOrderNetworking.MOD_ID)
public final class ICreateOrderNetworking {
    public static final String MOD_ID = "icreate_order_networking";

    public ICreateOrderNetworking(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
    }
}
