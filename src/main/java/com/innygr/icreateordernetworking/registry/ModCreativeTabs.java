package com.innygr.icreateordernetworking.registry;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative inventory tab for all items and blocks provided by the mod. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ICreateOrderNetworking.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.icreate_order_networking"))
                    .icon(() -> new ItemStack(ModBlocks.ORDER_TERMINAL_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.ORDER_TERMINAL_ITEM.get());
                        output.accept(ModBlocks.TERMINAL_LOGGER_ITEM.get());
                        output.accept(ModBlocks.BRASS_TERMINAL_LOGGER_ITEM.get());
                        output.accept(ModBlocks.NETWORK_STATISTICS_ITEM.get());
                        output.accept(ModBlocks.SCRAPPER_ITEM.get());
                        output.accept(ModBlocks.MISSILE_THRUSTER_ITEM.get());
                        output.accept(ModBlocks.MISSILE_TAG_ITEM.get());
                        output.accept(ModItems.MISSILE_FIN.get());
                        output.accept(ModItems.MISSILE_NOSE.get());
                        output.accept(ModItems.ALTITUDE_SENSOR.get());
                        output.accept(ModItems.NAVIGATION_TABLE.get());
                        output.accept(ModItems.MISSILE_NAVIGATOR.get());
                        output.accept(ModItems.NUCLEAR_CORE.get());
                    }).build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
