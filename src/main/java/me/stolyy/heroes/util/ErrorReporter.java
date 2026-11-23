package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Logs an exception as SEVERE and, when {@code discord.error-webhook} is set in game-config.yml, posts it
 * to Discord with the game it happened in. Posts are deduplicated per exception class + top frame for
 * {@code discord.error-dedupe-minutes} and capped at {@code discord.error-max-per-hour}.
 */
public final class ErrorReporter {
    private static final Map<String, Long> lastPosted = new HashMap<>();
    private static long hourWindowStart;
    private static int postedThisHour;

    private ErrorReporter() {}

    public static void report(String context, Throwable error) {
        report(null, context, error);
    }

    public static void report(Game game, String context, Throwable error) {
        Heroes.getInstance().getLogger().log(Level.SEVERE, context, error);

        String url = GameConfigManager.getGameConfig().getString("discord.error-webhook", "");
        if (url.isEmpty() || !allow(error)) return;

        StringBuilder description = new StringBuilder();
        description.append("**").append(DiscordWebhook.escape(context)).append("**\n");
        if (game != null) description.append(DiscordWebhook.escape(describe(game))).append('\n');
        description.append("```\n").append(DiscordWebhook.escape(stackTop(error, 8))).append("```");

        String json = "{\"embeds\":[{\"title\":\"" + DiscordWebhook.escape(error.getClass().getSimpleName())
                + "\",\"description\":\"" + description + "\",\"color\":15158332,\"footer\":{\"text\":\"Heroes "
                + DiscordWebhook.escape(Heroes.version()) + "\"}}]}";
        DiscordWebhook.post(url, json);
    }

    private static boolean allow(Throwable error) {
        long now = System.currentTimeMillis();
        long dedupeMs = GameConfigManager.getGameConfig().getInt("discord.error-dedupe-minutes", 10) * 60_000L;
        int maxPerHour = GameConfigManager.getGameConfig().getInt("discord.error-max-per-hour", 20);

        if (now - hourWindowStart > 3_600_000L) {
            hourWindowStart = now;
            postedThisHour = 0;
        }
        if (postedThisHour >= maxPerHour) return false;

        StackTraceElement[] trace = error.getStackTrace();
        String key = error.getClass().getName() + "@" + (trace.length > 0 ? trace[0].toString() : "?");
        Long last = lastPosted.get(key);
        if (last != null && now - last < dedupeMs) return false;

        lastPosted.put(key, now);
        postedThisHour++;
        return true;
    }

    private static String describe(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.gameMode()).append(" / ").append(game.modeConfig().winCondition())
                .append(" on ").append(game.settings().map().name()).append(", ").append(game.gameState()).append('\n');
        for (GamePlayer gp : game.getGamePlayers().values()) {
            sb.append("  ").append(gp.team()).append(' ').append(participantName(gp))
                    .append(" (").append(gp.currentHeroName()).append(')');
            if (gp.leftGame()) sb.append(" [left]");
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String participantName(GamePlayer gp) {
        if (gp.bot() != null) return gp.bot().getName() + " [bot]";
        UUID uuid = gp.uuid();
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() != null ? player.getName() : uuid.toString();
    }

    private static String stackTop(Throwable error, int frames) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, frames + 1); i++) sb.append(lines[i].trim()).append('\n');
        return sb.toString();
    }
}
