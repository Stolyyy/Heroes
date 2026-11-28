package me.stolyy.heroes.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/** Discord webhook HTTP helpers (post, post-for-id, edit), off the main thread except shutdown sync edit. */
public final class DiscordWebhook {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);

    public enum EditResult { OK, NOT_FOUND, FAILED }

    private DiscordWebhook() {}

    /** Builds a JSON content payload that forbids all Discord mentions (roles, users, @everyone). */
    public static String contentPayload(String content) {
        return "{\"content\":\"" + escape(content) + "\",\"allowed_mentions\":{\"parse\":[]}}";
    }

    /** Posts {@code json} (a Discord webhook payload) to {@code url}. Failures are logged, never thrown into game code. */
    public static void post(String url, String json) {
        Bukkit.getScheduler().runTaskAsynchronously(Heroes.getInstance(), () -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
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

    /**
     * Posts {@code json} with {@code ?wait=true} off the main thread and passes the created message ID
     * to {@code onSuccess}. Failures are logged and invoke {@code onFailure}, never throwing into game code.
     */
    public static void postForId(String url, String json, Consumer<String> onSuccess, Runnable onFailure) {
        Bukkit.getScheduler().runTaskAsynchronously(Heroes.getInstance(), () -> {
            String waitUrl = url.contains("?") ? url + "&wait=true" : url + "?wait=true";
            HttpRequest request = HttpRequest.newBuilder(URI.create(waitUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            try {
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonElement idElem = obj.get("id");
                    if (idElem != null && !idElem.isJsonNull()) {
                        onSuccess.accept(idElem.getAsString());
                        return;
                    }
                    Heroes.getInstance().getLogger().warning("Discord webhook ?wait=true response missing message id: " + response.body());
                } else {
                    Heroes.getInstance().getLogger().warning("Discord webhook postForId returned " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                Heroes.getInstance().getLogger().warning("Discord webhook postForId failed: " + e);
            }
            onFailure.run();
        });
    }

    /** Edits an existing webhook message off the main thread, reporting {@link EditResult} to {@code onResult}. */
    public static void edit(String url, String messageId, String json, Consumer<EditResult> onResult) {
        Bukkit.getScheduler().runTaskAsynchronously(Heroes.getInstance(), () ->
                onResult.accept(sendEdit(url, messageId, json, REQUEST_TIMEOUT)));
    }

    /** Synchronous edit with a short timeout for plugin shutdown when async tasks cannot be scheduled. */
    public static EditResult editSync(String url, String messageId, String json) {
        return sendEdit(url, messageId, json, SHUTDOWN_TIMEOUT);
    }

    private static EditResult sendEdit(String url, String messageId, String json, Duration timeout) {
        String base = stripQuery(url);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String editUrl = base + "/messages/" + messageId;
        HttpRequest request = HttpRequest.newBuilder(URI.create(editUrl))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) return EditResult.OK;
            if (code == 404) return EditResult.NOT_FOUND;
            Heroes.getInstance().getLogger().warning("Discord webhook edit returned " + code + ": " + response.body());
            return EditResult.FAILED;
        } catch (Exception e) {
            Heroes.getInstance().getLogger().warning("Discord webhook edit failed: " + e);
            return EditResult.FAILED;
        }
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    /** Backslash-escapes Discord markdown metacharacters so player/hero names cannot break spoilers or formatting. */
    public static String escapeMarkdown(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            if (c == '\\' || c == '_' || c == '*' || c == '|' || c == '~' || c == '`') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
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
