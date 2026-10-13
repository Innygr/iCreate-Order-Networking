package com.innygr.icreateordernetworking.radiation;

import com.innygr.icreateordernetworking.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/** Server-authoritative radiation exposure, rain cleanup, and cloud particles. */
@EventBusSubscriber(modid = "icreate_order_networking")
public final class RadiationService {
    private static final int DEFAULT_RADIUS = 32;
    private static final int EFFECT_REFRESH_TICKS = 20;
    private static final DustParticleOptions GREEN_CLOUD =
            new DustParticleOptions(new Vector3f(0.12F, 0.95F, 0.18F), 2.0F);

    private RadiationService() {}

    /** Call from a successful nuclear-payload detonation. */
    public static void createBlastZone(ServerLevel level, BlockPos impact, int radius) {
        RadiationZoneData.get(level).add(impact, radius > 0 ? radius : DEFAULT_RADIUS);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        if (player.tickCount % EFFECT_REFRESH_TICKS != 0) return;

        for (RadiationZoneData.Zone zone : RadiationZoneData.get(level).zones()) {
            double dx = player.getX() - (zone.center().getX() + 0.5D);
            double dy = player.getY() - (zone.center().getY() + 0.5D);
            double dz = player.getZ() - (zone.center().getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz <= (double) zone.radius() * zone.radius()) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.RADIATION, 80, 0, true, true, true));
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 10 != 0) return;
        RadiationZoneData data = RadiationZoneData.get(level);
        for (RadiationZoneData.Zone zone : data.zones()) {
            BlockPos center = zone.center();
            // Rain at the zone clears it permanently; dry weather leaves it active.
            if (level.isRainingAt(center)) {
                data.remove(zone);
                continue;
            }

            // Sparse green dust marks the contaminated volume without spawning entities.
            int radius = zone.radius();
            for (int i = 0; i < 12; i++) {
                double angle = (level.getGameTime() * 0.025D) + (Math.PI * 2.0D * i / 12.0D);
                double x = center.getX() + 0.5D + Math.cos(angle) * radius * 0.72D;
                double z = center.getZ() + 0.5D + Math.sin(angle) * radius * 0.72D;
                double y = center.getY() + 1.5D + (i % 4) * Math.max(1, radius / 10);
                level.sendParticles(GREEN_CLOUD, x, y, z, 2, 0.4D, 0.5D, 0.4D, 0.015D);
            }
        }
    }
}
