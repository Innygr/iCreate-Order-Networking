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

    /** Replaceable aerodynamic stabilizer component for missile assemblies. */
    public static final DeferredItem<Item> MISSILE_FIN =
            ITEMS.register("missile_fin", () -> new Item(new Item.Properties()));

    /** Nose/cone component for missile assemblies. */
    public static final DeferredItem<Item> MISSILE_NOSE =
            ITEMS.register("missile_nose", () -> new Item(new Item.Properties()));

    /** Altitude-reading component used by missile guidance equipment. */
    public static final DeferredItem<Item> ALTITUDE_SENSOR =
            ITEMS.register("altitude_sensor", () -> new Item(new Item.Properties()));

    /** Map-planning component used to assemble a missile navigator. */
    public static final DeferredItem<Item> NAVIGATION_TABLE =
            ITEMS.register("navigation_table", () -> new Item(new Item.Properties()));

    /** Guidance unit assembled from an altitude sensor, navigation table, and cartography table. */
    public static final DeferredItem<Item> MISSILE_NAVIGATOR =
            ITEMS.register("missile_navigator", () -> new Item(new Item.Properties().stacksTo(1)));

    /** Assembly marker for a future nuclear missile payload. */
    public static final DeferredItem<Item> NUCLEAR_CORE =
            ITEMS.register("nuclear_core", () -> new Item(new Item.Properties().stacksTo(1)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
