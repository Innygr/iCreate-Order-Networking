package com.innygr.icreateordernetworking.client;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "icreate_order_networking", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModPonderClient {
    private ModPonderClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new ModPonderPlugin()));
    }
}