package com.innygr.icreateordernetworking.registry;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registered sound events used by the Scrapper. Audio files are supplied as resource assets. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, ICreateOrderNetworking.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GRINDER_IDLE_LOOP =
            register("grinder_idle_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRINDER_BEGIN =
            register("grinder_begin");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRINDER_ACTIVE_LOOP =
            register("grinder_active_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRINDER_METAL =
            register("grinder_metal");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ICreateOrderNetworking.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }

    private ModSounds() {}
}
