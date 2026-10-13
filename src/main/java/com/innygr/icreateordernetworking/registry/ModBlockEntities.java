package com.innygr.icreateordernetworking.registry;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.innygr.icreateordernetworking.blocks.NetworkStatisticsBlockEntity;
import com.innygr.icreateordernetworking.blocks.OrderTerminalBlockEntity;
import com.innygr.icreateordernetworking.blocks.ScrapperBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,
                    ICreateOrderNetworking.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalLoggerBlockEntity>> TERMINAL_LOGGER =
            BLOCK_ENTITIES.register("terminal_logger",
                    () -> BlockEntityType.Builder.of(TerminalLoggerBlockEntity::new,
                            ModBlocks.TERMINAL_LOGGER.get(), ModBlocks.BRASS_TERMINAL_LOGGER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkStatisticsBlockEntity>> NETWORK_STATISTICS =
            BLOCK_ENTITIES.register("network_statistics",
                    () -> BlockEntityType.Builder.of(NetworkStatisticsBlockEntity::new,
                            ModBlocks.NETWORK_STATISTICS.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrderTerminalBlockEntity>> ORDER_TERMINAL =
            BLOCK_ENTITIES.register("order_terminal",
                    () -> BlockEntityType.Builder.of(OrderTerminalBlockEntity::new,
                            ModBlocks.ORDER_TERMINAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScrapperBlockEntity>> SCRAPPER =
            BLOCK_ENTITIES.register("scrapper",
                    () -> BlockEntityType.Builder.of(ScrapperBlockEntity::new,
                            ModBlocks.SCRAPPER.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
