package com.innygr.icreateordernetworking.mythic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the server-admin-managed mapping between FTB Teams IDs and Discord
 * team names. The mapping is local to the dedicated server and contains no
 * bot credentials. It is deliberately read-only: this bridge never edits FTB
 * Teams membership or grants permissions.
 */
public final class FtbTeamsSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("iCreateOrderNetworking/FtbTeams");
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("icreate-order-networking-mythic-teams.json");
    private static volatile Map<String, TeamMapping> mappings = Collections.emptyMap();

    private FtbTeamsSync() {}

    static void load() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH.getParent());
                Files.writeString(CONFIG_PATH, defaultConfig(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion") == null || root.get("schemaVersion").getAsInt() != 1
                    || root.get("teams") == null || !root.get("teams").isJsonObject()) {
                throw new IllegalStateException("expected schemaVersion 1 and a teams object");
            }

            Map<String, TeamMapping> loaded = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("teams").entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject team = entry.getValue().getAsJsonObject();
                String name = readString(team, "name");
                String discordRoleId = readString(team, "discordRoleId");
                if (name.isBlank()) {
                    LOGGER.warn("Ignoring FTB team mapping '{}' because its name is blank.", entry.getKey());
                    continue;
                }
                loaded.put(entry.getKey(), new TeamMapping(name, discordRoleId));
            }
            mappings = Collections.unmodifiableMap(loaded);
            LOGGER.info("Loaded {} Mythic FTB team mapping(s).", loaded.size());
        } catch (IOException | RuntimeException exception) {
            mappings = Collections.emptyMap();
            LOGGER.warn("Could not load Mythic FTB team mappings from {}: {}", CONFIG_PATH, exception.getMessage());
        }
    }

    public static TeamMapping find(String ftbTeamId) {
        return ftbTeamId == null ? null : mappings.get(ftbTeamId);
    }

    public static String displayName(String ftbTeamId, String fallback) {
        TeamMapping mapping = find(ftbTeamId);
        return mapping == null ? (fallback == null || fallback.isBlank() ? "Unassigned team" : fallback) : mapping.name();
    }

    private static String readString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString().strip() : "";
    }

    private static String defaultConfig() {
        return """
                {
                  "schemaVersion": 1,
                  "teams": {
                    "replace-with-ftb-team-id": {
                      "name": "Example team",
                      "discordRoleId": ""
                    }
                  }
                }
                """;
    }

    public record TeamMapping(String name, String discordRoleId) {}
}
