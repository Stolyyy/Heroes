package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Fire-and-forget Discord webhook posts, always off the main thread. */
public final class DiscordWebhook {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private DiscordWebhook() {}

    /** Posts {@code json} (a Discord webhook payload) to {@code url}. Failures are logged, never thrown into game code. */
    public static void post(String url, String json) {
        Bukkit.getScheduler().runTaskAsynchronously(Heroes.getInstance(), () -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            try {
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    Heroes.getInstance().getLogger().warning("Discord webhook returned " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                Heroes.getInstance().getLogger().warning("Discord webhook post failed: " + e);
            }
        });
    }

    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> {}
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.toString();
    }
}
