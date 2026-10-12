package com.innygr.icreateordernetworking.orders;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Initial team-permission helper using vanilla scoreboard teams, avoiding a
 * mandatory client-side team mod. Order creation must snapshot the team ID
 * into the order; do not infer the order's owner team dynamically later.
 */
public final class OrderTeamAccess {
    private OrderTeamAccess() {}

    /** Stable-enough persisted team key for the current scoreboard team. */
    public static String teamKey(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        PlayerTeam team = player.getServer().getScoreboard().getPlayersTeam(player.getGameProfile().getName());
        return team == null ? null : team.getName();
    }

    public static boolean isOnTeam(ServerPlayer player, String teamKey) {
        return teamKey != null && Objects.equals(teamKey(player), teamKey);
    }

    /**
     * Personal orders are private to their owner. Team orders can be accessed
     * by current members of the same vanilla scoreboard team.
     */
    public static boolean canAccess(ServerPlayer player, UUID ownerId, String ownerTeamKey) {
        if (player == null || ownerId == null) return false;
        if (ownerId.equals(player.getUUID())) return true;
        return ownerTeamKey != null && isOnTeam(player, ownerTeamKey);
    }

    /** A team member cannot claim their own team's request as an outside worker. */
    public static boolean canClaim(ServerPlayer player, UUID ownerId, String ownerTeamKey) {
        if (player == null || ownerId == null || ownerId.equals(player.getUUID())) return false;
        return ownerTeamKey == null || !isOnTeam(player, ownerTeamKey);
    }
}
