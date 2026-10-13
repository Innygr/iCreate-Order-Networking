package com.innygr.icreateordernetworking.missile;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Validates the anchor height and exact relative block pattern for an orbital missile. */
public final class OrbitalMissileValidator {
    public static final int MINIMUM_ANCHOR_Y = 250;

    private OrbitalMissileValidator() {}

    /**
     * Checks a required relative pattern. The anchor must be at Y=250 or higher,
     * and each expected block must exist at its rotated, upside-down position.
     * Pattern offsets are relative to the anchor in the unrotated blueprint.
     */
    public static boolean matchesPattern(ServerLevel level, BlockPos anchor,
            Map<BlockPos, ? extends Block> requiredPattern, Direction horizontalFacing) {
        if (anchor.getY() < MINIMUM_ANCHOR_Y || horizontalFacing == null
                || !horizontalFacing.getAxis().isHorizontal() || requiredPattern == null
                || requiredPattern.isEmpty()) {
            return false;
        }

        for (Map.Entry<BlockPos, ? extends Block> entry : requiredPattern.entrySet()) {
            BlockPos offset = entry.getKey();
            BlockPos rotated = rotateUpsideDown(offset, horizontalFacing);
            BlockPos target = anchor.offset(rotated);
            if (!level.hasChunkAt(target)) return false;
            BlockState actual = level.getBlockState(target);
            if (!actual.is(entry.getValue())) return false;
        }
        return true;
    }

    /** Rotate a blueprint offset 180 degrees around its local X axis, then face it horizontally. */
    public static BlockPos rotateUpsideDown(BlockPos offset, Direction horizontalFacing) {
        int x = offset.getX();
        int y = -offset.getY();
        int z = -offset.getZ();
        return switch (horizontalFacing) {
            case NORTH -> new BlockPos(-x, y, z);
            case SOUTH -> new BlockPos(x, y, -z);
            case EAST -> new BlockPos(z, y, x);
            case WEST -> new BlockPos(-z, y, -x);
            default -> throw new IllegalArgumentException("Facing must be horizontal");
        };
    }
}
