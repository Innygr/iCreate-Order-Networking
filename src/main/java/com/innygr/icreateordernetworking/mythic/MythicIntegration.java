package com.innygr.icreateordernetworking.mythic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Optional server-only Mythic integration, compiled only in the Mythic build. */
public final class MythicIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("iCreateOrderNetworking/Mythic");
    private static final Path CONFIG_PATH = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve("icreate-order-networking-mythic.json");
    private static volatile MythicIntegration active;
    private boolean enabled;
    private boolean registered;

    private String webhookUrl = "";
    private String serverName = "Minecraft server";
    private String apiBaseUrl = "";
    private String apiToken = "";

    private MythicIntegration() {}

    public static void onServerStarted(MinecraftServer server) {
        MythicIntegration integration = new MythicIntegration();
        integration.loadConfig();
        FtbTeamsSync.load();
        active = integration;
        BotApiBridge.configure(integration.apiBaseUrl, integration.apiToken);
        if (integration.enabled) {
            NeoForge.EVENT_BUS.register(integration);
            integration.registered = true;
            DiscordBridge.configure(true, integration.webhookUrl, integration.serverName);
            DiscordBridge.sendEvent("Server online", "Mythic integration connected.");
            LOGGER.info("Mythic server integration enabled.");
        } else {
            DiscordBridge.configure(false, "", integration.serverName);
            LOGGER.info("Mythic server integration disabled; configure a valid webhook or bot API.");
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        MythicIntegration integration = active;
        if (integration != null && integration.registered) NeoForge.EVENT_BUS.unregister(integration);
        DiscordBridge.sendEvent("Server offline", "The Minecraft server is shutting down.");
        DiscordBridge.shutdown();
        active = null;
    }

    /** Call when the missile assembly is successfully launched. */
    public static void missileLaunched(UUID missileId, String teamId, String teamName, String target) {
        DiscordBridge.sendEvent("Missile launched",
                "Missile: " + safeId(missileId) + "\nTeam: " + FtbTeamsSync.displayName(teamId, teamName)
                        + "\nTarget: " + safe(target));
    }

    /** Call once when a missile impact is resolved. */
    public static void missileImpacted(UUID missileId, String teamId, String teamName, String result, String location) {
        DiscordBridge.sendEvent("Missile impact",
                "Missile: " + safeId(missileId) + "\nTeam: " + FtbTeamsSync.displayName(teamId, teamName)
                        + "\nResult: " + safe(result) + "\nLocation: " + safe(location));
    }

    /** Call once when defence/interception code confirms an interception. */
    public static void missileIntercepted(UUID missileId, String interceptor, String location) {
        DiscordBridge.sendEvent("Missile intercepted",
                "Missile: " + safeId(missileId) + "\nIntercepted by: " + safe(interceptor)
                        + "\nLocation: " + safe(location));
    }

    private void loadConfig() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH.getParent());
                Files.writeString(CONFIG_PATH, """
                        {
                          "enabled": false,
                          "serverName": "Minecraft server",
                          "discordWebhookUrl": "",
                          "apiBaseUrl": "",
                          "apiToken": ""
                        }
                        """, StandardCharsets.UTF_8);
            }
            com.google.gson.JsonObject config = com.google.gson.JsonParser
                    .parseString(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            enabled = config.has("enabled") && config.get("enabled").getAsBoolean();
            serverName = config.has("serverName") ? config.get("serverName").getAsString() : "Minecraft server";
            webhookUrl = config.has("discordWebhookUrl") ? config.get("discordWebhookUrl").getAsString() : "";
            apiBaseUrl = config.has("apiBaseUrl") ? config.get("apiBaseUrl").getAsString() : "";
            apiToken = config.has("apiToken") ? config.get("apiToken").getAsString() : "";

            boolean webhookReady = isValidWebhook(webhookUrl);
            boolean apiReady = apiBaseUrl != null && !apiBaseUrl.isBlank() && apiToken != null && !apiToken.isBlank();
            if (enabled && !webhookReady && !apiReady) {
                enabled = false;
                LOGGER.warn("Mythic integration disabled: configure a valid Discord webhook or HTTPS bot API URL plus apiToken.");
            }
            if (!webhookReady) webhookUrl = "";
        } catch (Exception exception) {
            enabled = false;
            LOGGER.warn("Could not load Mythic integration config from {}: {}", CONFIG_PATH, exception.getMessage());
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            DiscordBridge.sendEvent("Player joined", player.getGameProfile().getName());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            DiscordBridge.sendEvent("Player left", player.getGameProfile().getName());
    }

    private static boolean isValidWebhook(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getHost().equalsIgnoreCase("discord.com") && uri.getPath() != null
                    && uri.getPath().startsWith("/api/webhooks/");
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static String safeId(UUID id) { return id == null ? "unknown" : id.toString(); }
    private static String safe(String value) { return value == null || value.isBlank() ? "unknown" : value.strip(); }
}
