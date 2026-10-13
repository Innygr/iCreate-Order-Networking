package com.innygr.icreateordernetworking.registry;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items owned by iCreate: Order Networking. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ICreateOrderNetworking.MOD_ID);

    /** Assembly marker for a future nuclear missile payload. */
    public static final DeferredItem<Item> NUCLEAR_CORE =
            ITEMS.register("nuclear_core", () -> new Item(new Item.Properties().stacksTo(1)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
