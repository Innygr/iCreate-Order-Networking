package com.innygr.icreateordernetworking.missile;

import com.innygr.icreateordernetworking.blocks.MissileTagBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Creates attribution text for deaths caused by a missile impact. */
public final class MissileDeathMessages {
    private MissileDeathMessages() {}

    /**
     * Returns “victim was exploded via Team's missile” when the optional tag has
     * a team, or the generic message otherwise. The impact/death handler should
     * use this component when applying missile-caused player deaths.
     */
    public static Component explodedByTeam(Entity victim, MissileTagBlockEntity tag) {
        Component victimName = victim.getDisplayName();
        if (tag == null || tag.getTeamName().isBlank()) {
            return Component.translatable("death.attack.icreate_order_networking.missile", victimName);
        }
        return Component.translatable("death.attack.icreate_order_networking.team_missile",
                victimName, Component.literal(tag.getTeamName()));
    }
}
