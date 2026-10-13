package com.innygr.icreateordernetworking;

import com.innygr.icreateordernetworking.compat.NetworkStatisticsDisplaySource;
import com.innygr.icreateordernetworking.network.ModNetwork;
import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.registry.ModBlocks;
import com.innygr.icreateordernetworking.registry.ModEffects;
import com.innygr.icreateordernetworking.registry.ModItems;
import com.innygr.icreateordernetworking.registry.ModSounds;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Main entry point for iCreate: Order Networking. */
@Mod(ICreateOrderNetworking.MOD_ID)
public final class ICreateOrderNetworking {
    public static final String MOD_ID = "icreate_order_networking";

    public ICreateOrderNetworking(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModItems.register(modBus);
        ModEffects.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(ModNetwork::registerPayloads);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::registerDisplaySources);
        modBus.addListener(this::registerDisplaySourceBindings);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.TERMINAL_LOGGER.get(),
                (blockEntity, side) -> blockEntity.getSidedItemHandler(side));
    }

    private void registerDisplaySources(RegisterEvent event) {
        event.register(CreateRegistries.DISPLAY_SOURCE,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "network_statistics"),
                () -> NetworkStatisticsDisplaySource.INSTANCE);
    }

    private void registerDisplaySourceBindings(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DisplaySource.BY_BLOCK_ENTITY.add(
                ModBlockEntities.NETWORK_STATISTICS.get(),
                NetworkStatisticsDisplaySource.INSTANCE));
    }
}
