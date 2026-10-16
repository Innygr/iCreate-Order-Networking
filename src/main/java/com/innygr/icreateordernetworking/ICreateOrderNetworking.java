package com.innygr.icreateordernetworking;

import com.innygr.icreateordernetworking.compat.NetworkStatisticsDisplaySource;
import com.innygr.icreateordernetworking.compat.OrderTerminalDisplaySource;
import com.innygr.icreateordernetworking.compat.TerminalLoggerDisplaySource;
import com.innygr.icreateordernetworking.compat.RedstoneRequesterOrderBridge;
import com.innygr.icreateordernetworking.compat.CreateStockLinkEndpointBridge;
import com.innygr.icreateordernetworking.delivery.AutomatedDeliveryRegistry;
import com.innygr.icreateordernetworking.delivery.AeronauticsDockingConnectorAdapter;
import com.innygr.icreateordernetworking.delivery.DurableShipmentService;
import com.innygr.icreateordernetworking.delivery.PortableStorageInterfaceTrainAdapter;
import com.innygr.icreateordernetworking.delivery.PackagerFrogportAdapter;
import com.innygr.icreateordernetworking.logistics.FactoryEndpointRegistry;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Main entry point for iCreate: Order Networking. */
@Mod(ICreateOrderNetworking.MOD_ID)
public final class ICreateOrderNetworking {
    public static final String MOD_ID = "icreate_order_networking";

    private final RedstoneRequesterOrderBridge requesterOrderBridge = new RedstoneRequesterOrderBridge();
    private final CreateStockLinkEndpointBridge stockLinkEndpointBridge = new CreateStockLinkEndpointBridge();

    public ICreateOrderNetworking(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(requesterOrderBridge);
        NeoForge.EVENT_BUS.register(stockLinkEndpointBridge);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
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

    private void onServerStarted(ServerStartedEvent event) {
        // The registry is cleared on shutdown, so install stateless adapters for each server lifecycle.
        AutomatedDeliveryRegistry.register(new PortableStorageInterfaceTrainAdapter());
        AutomatedDeliveryRegistry.register(new AeronauticsDockingConnectorAdapter());
        AutomatedDeliveryRegistry.register(new PackagerFrogportAdapter());
        // Retry durable payouts only after a destination receipt was already recorded.
        // Transport adapters still own recovery for shipments awaiting physical reconciliation.
        DurableShipmentService.recoverPendingPayouts(event.getServer().overworld());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        requesterOrderBridge.clear();
        stockLinkEndpointBridge.clear();
        AutomatedDeliveryRegistry.clear();
        FactoryEndpointRegistry.clear();
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
        event.register(CreateRegistries.DISPLAY_SOURCE,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "terminal_logger"),
                () -> TerminalLoggerDisplaySource.INSTANCE);
        event.register(CreateRegistries.DISPLAY_SOURCE,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "order_terminal"),
                () -> OrderTerminalDisplaySource.INSTANCE);
    }

    private void registerDisplaySourceBindings(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK_ENTITY.add(
                    ModBlockEntities.NETWORK_STATISTICS.get(),
                    NetworkStatisticsDisplaySource.INSTANCE);
            DisplaySource.BY_BLOCK_ENTITY.add(
                    ModBlockEntities.TERMINAL_LOGGER.get(),
                    TerminalLoggerDisplaySource.INSTANCE);
            DisplaySource.BY_BLOCK_ENTITY.add(
                    ModBlockEntities.ORDER_TERMINAL.get(),
                    OrderTerminalDisplaySource.INSTANCE);
        });
    }

}
