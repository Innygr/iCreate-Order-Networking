package com.innygr.icreateordernetworking.registry;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
                    () -> new TerminalLoggerBlock(BlockBehaviour.Properties.of().strength(3.0F)));
    public static final DeferredItem<BlockItem> TERMINAL_LOGGER_ITEM =
            ITEMS.register("terminal_logger",
                    () -> new BlockItem(TERMINAL_LOGGER.get(), new Item.Properties()));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
