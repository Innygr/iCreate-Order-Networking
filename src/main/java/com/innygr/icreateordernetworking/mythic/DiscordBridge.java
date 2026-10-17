package com.innygr.icreateordernetworking.mythic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-only Discord notifier for the optional Mythic build.
 *
 * A Discord webhook is used instead of a bot token: it is configured only on
 * the dedicated server and is never sent to clients or embedded in the JAR.
 */
public final class DiscordBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("iCreateOrderNetworking/MythicDiscord");
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("icreate-order-networking-mythic.json");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "icreate-mythic-discord");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile String webhookUrl = "";
    private static volatile String serverName = "Minecraft server";
    private static volatile boolean enabled;
    private static volatile boolean stopped;

    private DiscordBridge() {}

    static void configure(boolean isEnabled, String webhook, String configuredServerName) {
        enabled = isEnabled;
        webhookUrl = webhook == null ? "" : webhook.strip();
        serverName = configuredServerName == null || configuredServerName.isBlank()
                ? "Minecraft server" : configuredServerName.strip();
        stopped = false;
    }

    public static boolean isConfigured() {
        return enabled && isHttpsWebhook(webhookUrl);
    }

    public static void sendEvent(String eventType, String details) {
        if (stopped || !isConfigured()) return;
        String safeType = sanitize(eventType, 80);
        String safeDetails = sanitize(details, 1500);
        JsonObject payload = new JsonObject();
        payload.addProperty("content", "**[" + serverName + "] " + safeType + "**\n" + safeDetails);
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new com.google.gson.JsonArray());
        payload.add("allowed_mentions", allowedMentions);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Mythic Discord webhook URL is invalid; event was not sent.");
            return;
        }

        EXECUTOR.submit(() -> {
            try {
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    LOGGER.warn("Mythic Discord webhook returned HTTP {}; event was not delivered.", response.statusCode());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Unable to deliver Mythic Discord event: {}", exception.getMessage());
            }
        });
    }

    static void shutdown() {
        stopped = true;
    }

    private static boolean isHttpsWebhook(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getHost().equalsIgnoreCase("discord.com")
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/api/webhooks/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return "n/a";
        String normalized = value.replace("@everyone", "@\u200beveryone")
                .replace("@here", "@\u200bhere")
                .replace("\r", " ")
                .strip();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
