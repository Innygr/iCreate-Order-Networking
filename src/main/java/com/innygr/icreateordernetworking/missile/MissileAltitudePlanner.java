package com.innygr.icreateordernetworking.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Plans a fuel-conscious cruise altitude before launch. The planner samples
 * the direct corridor, raises cruise altitude only enough to clear terrain,
 * and refuses to plan through chunks that are not already loaded.
 */
public final class MissileAltitudePlanner {
    public static final int DEFAULT_CLEARANCE = 8;
    private static final int SAMPLE_SPACING = 4;
    private static final int BUILD_HEIGHT_MARGIN = 16;

    private MissileAltitudePlanner() {}

    public record FlightPlan(int cruiseY, int highestObstacleY, double estimatedTravelDistance,
                             boolean withinEstimatedRange) {}

    /**
     * @param level server world
     * @param launch launch position
     * @param target target position
     * @param maximumRange fuel-derived estimated travel budget in blocks
     * @return a plan, or null if the corridor requires loading an unloaded chunk
     */
    public static FlightPlan plan(ServerLevel level, BlockPos launch, BlockPos target, int maximumRange) {
        if (maximumRange <= 0) {
            return new FlightPlan(launch.getY(), launch.getY(), Double.POSITIVE_INFINITY, false);
        }

        double dx = target.getX() - launch.getX();
        double dz = target.getZ() - launch.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(horizontalDistance / SAMPLE_SPACING));
        int highestObstacle = Math.max(launch.getY(), target.getY());

        for (int i = 0; i <= samples; i++) {
            double fraction = (double) i / samples;
            int x = (int) Math.round(launch.getX() + dx * fraction);
            int z = (int) Math.round(launch.getZ() + dz * fraction);
            BlockPos sample = new BlockPos(x, launch.getY(), z);
            if (!level.hasChunkAt(sample)) {
                return null;
            }
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            highestObstacle = Math.max(highestObstacle, surfaceY);
        }

        int desiredCruiseY = Math.max(Math.max(launch.getY(), target.getY()), highestObstacle + DEFAULT_CLEARANCE);
        int maxCruiseY = level.getMaxBuildHeight() - BUILD_HEIGHT_MARGIN;
        int cruiseY = Math.min(desiredCruiseY, maxCruiseY);

        // Estimate the vertical climb, horizontal cruise, and descent. This
        // intentionally favours the lowest safe cruise altitude over excess ascent.
        double ascent = Math.max(0, cruiseY - launch.getY());
        double descent = Math.max(0, cruiseY - target.getY());
        double estimatedDistance = ascent + horizontalDistance + descent;
        return new FlightPlan(cruiseY, highestObstacle, estimatedDistance, estimatedDistance <= maximumRange);
    }
}
