package com.innygr.icreateordernetworking.missile;

import com.innygr.icreateordernetworking.blocks.MissileTagBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Dedicated death-message components for orbital missile impacts. */
public final class OrbitalMissileDeathMessages {
    private OrbitalMissileDeathMessages() {}

    public static Component struckByOrbitalMissile(Entity victim, MissileTagBlockEntity tag) {
        Component victimName = victim.getDisplayName();
        if (tag == null || tag.getTeamName().isBlank()) {
            return Component.translatable("death.attack.icreate_order_networking.orbital_missile", victimName);
        }
        return Component.translatable("death.attack.icreate_order_networking.team_orbital_missile",
                victimName, Component.literal(tag.getTeamName()));
    }
}
