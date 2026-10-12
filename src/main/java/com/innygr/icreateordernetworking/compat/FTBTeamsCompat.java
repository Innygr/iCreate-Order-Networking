package com.innygr.icreateordernetworking.compat;

import java.util.Optional;
import java.util.UUID;
import net.neoforged.fml.ModList;

/**
 * Optional FTB Teams integration.
 *
 * The jar filename is not a reliable way to detect a NeoForge mod; use the
 * declared mod id. All API access is reflective so a missing or incompatible
 * optional dependency cannot break loading this mod's common classes.
 */
public final class FTBTeamsCompat {
    public static final String MOD_ID = "ftbteams";

    private FTBTeamsCompat() {}

    /** True when FTB Teams is loaded, regardless of its installed jar filename. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * True when the known public API entry point exists. This is a class/API
     * compatibility check, not a guarantee that the server team manager has
     * completed startup.
     */
    public static boolean isIntegrationReady() {
        return isLoaded() && FTBTeamsApiBridge.isSupportedApiPresent();
    }

    /**
     * Resolve a player's current FTB party-team UUID.
     *
     * Players who are not in a party team return empty; personal player teams
     * are deliberately not treated as shared teams. Always resolve this live
     * instead of persisting a cached membership as proof of current access.
     */
    public static Optional<UUID> partyTeamId(UUID playerId) {
        if (playerId == null || !isIntegrationReady()) return Optional.empty();
        return FTBTeamsApiBridge.partyTeamId(playerId);
    }

    /** True only when both players currently resolve to the same party team. */
    public static boolean arePlayersInSameParty(UUID firstPlayerId, UUID secondPlayerId) {
        if (firstPlayerId == null || secondPlayerId == null) return false;
        Optional<UUID> firstTeam = partyTeamId(firstPlayerId);
        return firstTeam.isPresent() && firstTeam.equals(partyTeamId(secondPlayerId));
    }

    /** Isolate all optional API reflection behind this nested bridge. */
    private static final class FTBTeamsApiBridge {
        private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";

        private static boolean isSupportedApiPresent() {
            if (!isLoaded()) return false;
            try {
                Class<?> api = apiClass();
                Class<?> apiInterface = apiInterfaceClass();
                Object instance = api.getMethod("api").invoke(null);
                if (instance == null) return false;
                apiInterface.getMethod("isManagerLoaded");
                apiInterface.getMethod("getManager");
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        private static Optional<UUID> partyTeamId(UUID playerId) {
            try {
                Object api = apiClass().getMethod("api").invoke(null);
                Class<?> apiInterface = apiInterfaceClass();
                if (api == null || !Boolean.TRUE.equals(apiInterface
                        .getMethod("isManagerLoaded").invoke(api))) {
                    return Optional.empty();
                }

                Object manager = apiInterface.getMethod("getManager").invoke(api);
                if (manager == null) return Optional.empty();

                Class<?> managerInterface = Class.forName(
                        "dev.ftb.mods.ftbteams.api.TeamManager", false, FTBTeamsCompat.class.getClassLoader());
                Object result = managerInterface
                        .getMethod("getTeamForPlayerID", UUID.class)
                        .invoke(manager, playerId);
                if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                    return Optional.empty();
                }

                Object team = optional.get();
                Class<?> teamInterface = Class.forName(
                        "dev.ftb.mods.ftbteams.api.Team", false, FTBTeamsCompat.class.getClassLoader());
                if (team == null || !Boolean.TRUE.equals(
                        teamInterface.getMethod("isPartyTeam").invoke(team))) {
                    return Optional.empty();
                }

                Object id = teamInterface.getMethod("getTeamId").invoke(team);
                return id instanceof UUID teamId ? Optional.of(teamId) : Optional.empty();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Fail closed: API drift or team-manager startup races grant no access.
                return Optional.empty();
            }
        }

        private static Class<?> apiClass() throws ClassNotFoundException {
            return Class.forName(API_CLASS, false, FTBTeamsCompat.class.getClassLoader());
        }

        private static Class<?> apiInterfaceClass() throws ClassNotFoundException {
            return Class.forName(API_CLASS + "$API", false, FTBTeamsCompat.class.getClassLoader());
        }
    }
}
