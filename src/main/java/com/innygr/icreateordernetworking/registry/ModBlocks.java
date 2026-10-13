package com.innygr.icreateordernetworking.registry;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlock;
import com.innygr.icreateordernetworking.blocks.ScrapperBlock;
import com.innygr.icreateordernetworking.blocks.NetworkStatisticsBlock;
import com.innygr.icreateordernetworking.blocks.OrderTerminalBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ICreateOrderNetworking.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ICreateOrderNetworking.MOD_ID);

    public static final DeferredBlock<TerminalLoggerBlock> TERMINAL_LOGGER =
            BLOCKS.register("terminal_logger",
                    () -> new TerminalLoggerBlock(BlockBehaviour.Properties.of().strength(3.0F), false));
    public static final DeferredItem<BlockItem> TERMINAL_LOGGER_ITEM =
            ITEMS.register("terminal_logger",
                    () -> new BlockItem(TERMINAL_LOGGER.get(), new Item.Properties()));

    public static final DeferredBlock<TerminalLoggerBlock> BRASS_TERMINAL_LOGGER =
            BLOCKS.register("brass_terminal_logger",
                    () -> new TerminalLoggerBlock(BlockBehaviour.Properties.of().strength(3.0F), true));
    public static final DeferredItem<BlockItem> BRASS_TERMINAL_LOGGER_ITEM =
            ITEMS.register("brass_terminal_logger",
                    () -> new BlockItem(BRASS_TERMINAL_LOGGER.get(), new Item.Properties()));

    public static final DeferredBlock<NetworkStatisticsBlock> NETWORK_STATISTICS =
            BLOCKS.register("network_statistics",
                    () -> new NetworkStatisticsBlock(BlockBehaviour.Properties.of().strength(2.5F)));
    public static final DeferredItem<BlockItem> NETWORK_STATISTICS_ITEM =
            ITEMS.register("network_statistics",
                    () -> new BlockItem(NETWORK_STATISTICS.get(), new Item.Properties()));

    public static final DeferredBlock<OrderTerminalBlock> ORDER_TERMINAL =
            BLOCKS.register("order_terminal",
                    () -> new OrderTerminalBlock(BlockBehaviour.Properties.of().strength(3.0F)));
    public static final DeferredItem<BlockItem> ORDER_TERMINAL_ITEM =
            ITEMS.register("order_terminal",
                    () -> new BlockItem(ORDER_TERMINAL.get(), new Item.Properties()));

    public static final DeferredBlock<ScrapperBlock> SCRAPPER =
            BLOCKS.register("scrapper",
                    () -> new ScrapperBlock(BlockBehaviour.Properties.of().strength(2.5F)));
    public static final DeferredItem<BlockItem> SCRAPPER_ITEM =
            ITEMS.register("scrapper",
                    () -> new BlockItem(SCRAPPER.get(), new Item.Properties()));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
