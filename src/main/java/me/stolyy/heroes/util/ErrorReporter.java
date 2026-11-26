package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Logs errors as SEVERE and, when {@code discord.error-webhook} is set in game-config.yml, posts
 * them to Discord with game context. Posts are deduplicated and capped per hour.
 */
public final class ErrorReporter {
    private static final Map<String, Long> lastPosted = new HashMap<>();
    private static long hourWindowStart;
    private static int postedThisHour;

    private ErrorReporter() {}

    public static void report(String context) {
        report((Game) null, context);
    }

    public static void report(@Nullable Game game, String context) {
        Heroes.getInstance().getLogger().severe(context);
        post("Server Error", "msg@" + context, game, context, null);
    }

    public static void report(String context, Throwable error) {
        report(null, context, error);
    }

    public static void report(@Nullable Game game, String context, Throwable error) {
        Heroes.getInstance().getLogger().log(Level.SEVERE, context, error);
        StackTraceElement[] trace = error.getStackTrace();
        String key = error.getClass().getName() + "@" + (trace.length > 0 ? trace[0].toString() : "?");
        post(error.getClass().getSimpleName(), key, game, context, stackTop(error, 8));
    }

    private static void post(String title, String dedupeKey, @Nullable Game game,
                             String context, @Nullable String stack) {
        String url = GameConfigManager.getGameConfig().getString("discord.error-webhook", "");
        if (url.isEmpty() || !allow(dedupeKey)) return;

        StringBuilder description = new StringBuilder();
        description.append("**").append(context).append("**\n");
        if (game != null) description.append(describe(game)).append('\n');
        if (stack != null) description.append("```\n").append(stack).append("```");

        String json = "{\"embeds\":[{\"title\":\"" + DiscordWebhook.escape(title)
                + "\",\"description\":\"" + DiscordWebhook.escape(description.toString()) + "\",\"color\":15158332,\"footer\":{\"text\":\"Heroes "
                + DiscordWebhook.escape(Heroes.version()) + "\"}}]}";
        DiscordWebhook.post(url, json);
    }

    private static boolean allow(String key) {
        long now = System.currentTimeMillis();
        long dedupeMs = Configs.requireInt(GameConfigManager.getGameConfig(), "discord.error-dedupe-minutes") * 60_000L;
        int maxPerHour = Configs.requireInt(GameConfigManager.getGameConfig(), "discord.error-max-per-hour");

        if (now - hourWindowStart > 3_600_000L) {
            hourWindowStart = now;
            postedThisHour = 0;
        }
        if (postedThisHour >= maxPerHour) return false;

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
            String name = game.participantName(gp) + (gp.isBot() ? " [bot]" : "");
            sb.append("  ").append(gp.team()).append(' ').append(name)
                    .append(" (").append(gp.currentHeroName()).append(')');
            if (gp.leftGame()) sb.append(" [left]");
            sb.append('\n');
        }
        return sb.toString();
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
