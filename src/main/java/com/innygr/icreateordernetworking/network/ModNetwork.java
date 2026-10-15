package com.innygr.icreateordernetworking.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's serverbound gameplay payloads. */
public final class ModNetwork {
    private ModNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CreateOrderPacket.TYPE, CreateOrderPacket.STREAM_CODEC, CreateOrderPacket::handle);
        registrar.playToServer(ClaimOrderPacket.TYPE, ClaimOrderPacket.STREAM_CODEC, ClaimOrderPacket::handle);
        registrar.playToServer(FulfilOrderPacket.TYPE, FulfilOrderPacket.STREAM_CODEC, FulfilOrderPacket::handle);
        registrar.playToServer(OrderTerminalActionPacket.TYPE, OrderTerminalActionPacket.STREAM_CODEC, OrderTerminalActionPacket::handle);
    }
}
