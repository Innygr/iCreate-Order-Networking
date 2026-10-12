package com.innygr.icreateordernetworking.network;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's serverbound gameplay payloads. */
@EventBusSubscriber(modid = ICreateOrderNetworking.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CreateOrderPacket.TYPE, CreateOrderPacket.STREAM_CODEC, CreateOrderPacket::handle);
        registrar.playToServer(ClaimOrderPacket.TYPE, ClaimOrderPacket.STREAM_CODEC, ClaimOrderPacket::handle);
        registrar.playToServer(FulfilOrderPacket.TYPE, FulfilOrderPacket.STREAM_CODEC, FulfilOrderPacket::handle);
    }
}
