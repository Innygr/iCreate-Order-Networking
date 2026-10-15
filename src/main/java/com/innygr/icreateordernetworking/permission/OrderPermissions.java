package com.innygr.icreateordernetworking.permission;

import com.innygr.icreateordernetworking.blocks.OrderTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Shared server-authoritative checks for actions routed through an Order Terminal. */
public final class OrderPermissions {
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;
    private OrderPermissions() {}

    /** Never load chunks from packet-supplied positions. */
    public static boolean canUseTerminal(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || !(player.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !level.hasChunkAt(pos)
                || player.blockPosition().distSqr(pos) > MAX_INTERACTION_DISTANCE_SQUARED) return false;
        if (!(level.getBlockEntity(pos) instanceof OrderTerminalBlockEntity terminal)
                || !terminal.isConfigured()) return false;
        return terminal.canAccessFactory(player.getUUID());
    }
}
