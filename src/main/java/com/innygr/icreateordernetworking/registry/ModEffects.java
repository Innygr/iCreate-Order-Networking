package com.innygr.icreateordernetworking.registry;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import com.innygr.icreateordernetworking.radiation.RadiationEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registered custom status effects. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ICreateOrderNetworking.MOD_ID);

    public static final DeferredHolder<MobEffect, RadiationEffect> RADIATION =
            EFFECTS.register("radiation", RadiationEffect::new);

    private ModEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
