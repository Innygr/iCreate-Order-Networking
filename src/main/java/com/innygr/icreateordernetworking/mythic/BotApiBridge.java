package com.innygr.icreateordernetworking.mythic;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional authenticated outbound event client for the companion Discord bot.
 * The bot must expose POST /api/icreate/events and validate the bearer token.
 * Credentials remain in the dedicated server's config and are never sent to clients.
 */
public final class BotApiBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("iCreateOrderNetworking/BotApi");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "icreate-mythic-bot-api");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile URI endpoint;
    private static volatile String token = "";
    private static volatile boolean enabled;

    private BotApiBridge() {}

    static void configure(String baseUrl, String sharedToken) {
        enabled = false;
        endpoint = null;
        token = sharedToken == null ? "" : sharedToken.strip();
        if (baseUrl == null || baseUrl.isBlank() || token.isBlank()) return;
        try {
            URI base = URI.create(baseUrl.strip());
            if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                LOGGER.warn("Mythic bot API disabled: apiBaseUrl must be an HTTPS base URL without credentials, query, or fragment.");
                return;
            }
            String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
            endpoint = URI.create(base.getScheme() + "://" + base.getAuthority() + path + "/api/icreate/events");
            enabled = true;
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Mythic bot API disabled: invalid apiBaseUrl.");
        }
    }

    static void sendEvent(String eventType, String details) {
        URI target = endpoint;
        String secret = token;
        if (!enabled || target == null || secret.isBlank()) return;

        JsonObject body = new JsonObject();
        body.addProperty("source", "icreate-order-networking");
        body.addProperty("event", safe(eventType, 80));
        body.addProperty("details", safe(details, 1800));
        body.addProperty("timestamp", java.time.Instant.now().toString());

        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bearer " + secret)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Could not create Mythic bot API request.");
            return;
        }

        EXECUTOR.submit(() -> {
            try {
                HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    LOGGER.warn("Mythic bot API returned HTTP {}; event was not accepted.", response.statusCode());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                LOGGER.warn("Unable to deliver event to Mythic bot API: {}", exception.getMessage());
            }
        });
    }

    static String safe(String value, int maxLength) {
        if (value == null) return "unknown";
        String zeroWidthSpace = Character.toString(0x200B);
        String clean = value.replace("@everyone", "@" + zeroWidthSpace + "everyone")
                .replace("@here", "@" + zeroWidthSpace + "here")
                .replace('\r', ' ').strip();
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }
}
