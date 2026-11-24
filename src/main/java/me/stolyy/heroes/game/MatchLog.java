package me.stolyy.heroes.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.ErrorReporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/**
 * Appends one JSON line per finished (non-cancelled) game to {@code match-log/YYYY-MM.jsonl} in the
 * plugin data folder. Read offline by {@code tools/matchlog.py}.
 */
public final class MatchLog {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private MatchLog() {}

    public static void write(Game game, GameEndReason reason, Team winner) {
        long endedAt = System.currentTimeMillis();
        JsonObject line = new JsonObject();
        line.addProperty("version", Heroes.version());
        line.addProperty("endedAt", endedAt);
        line.addProperty("mode", game.gameMode().displayName());

        JsonObject preset = new JsonObject();
        preset.addProperty("name", game.gameModePreset().name());
        preset.addProperty("winCondition", game.modeConfig().winCondition().name());
        preset.addProperty("deathOutcome", game.modeConfig().deathOutcome().name());
        preset.addProperty("stocks", game.modeConfig().stocks());
        preset.addProperty("killLimit", game.modeConfig().killLimit());
        preset.addProperty("bestOf", game.modeConfig().bestOf());
        line.add("preset", preset);

        line.addProperty("map", game.settings().map().name());
        line.addProperty("durationMs", game.durationMs());
        line.addProperty("endReason", reason.name());
        line.addProperty("winner", winner == null ? null : winner.color().name());

        Game.SeriesRound seriesRound = game.seriesRound();
        if (seriesRound != null) {
            JsonObject series = new JsonObject();
            series.addProperty("id", seriesRound.id());
            series.addProperty("round", seriesRound.round());
            line.add("series", series);
        } else {
            line.add("series", null);
        }

        JsonArray participants = new JsonArray();
        for (Map.Entry<UUID, GamePlayer> entry : game.getGamePlayers().entrySet()) {
            GamePlayer gamePlayer = entry.getValue();
            TeamColor team = game.originalColor(entry.getKey());
            if (team == null || team == TeamColor.SPECTATOR) continue;
            JsonObject p = new JsonObject();
            p.addProperty("uuid", entry.getKey().toString());
            p.addProperty("name", game.participantName(gamePlayer));
            p.addProperty("team", team.name());
            p.addProperty("hero", gamePlayer.currentHeroName());
            p.addProperty("isBot", gamePlayer.isBot());
            p.addProperty("leftEarly", gamePlayer.leftGame());
            p.addProperty("kills", gamePlayer.kills());
            p.addProperty("deaths", gamePlayer.deaths());
            p.addProperty("damageDealt", gamePlayer.damageDealt());
            p.addProperty("damageTaken", gamePlayer.damageTaken());
            p.addProperty("voidDamageDealt", gamePlayer.voidDamageDealt());
            p.addProperty("voidDamageTaken", gamePlayer.voidDamageTaken());
            JsonObject byAbility = new JsonObject();
            gamePlayer.damageByAbility().forEach(byAbility::addProperty);
            p.add("damageByAbility", byAbility);
            participants.add(p);
        }
        line.add("participants", participants);

        Path dir = Heroes.getInstance().getDataFolder().toPath().resolve("match-log");
        Path file = dir.resolve(YearMonth.from(Instant.ofEpochMilli(endedAt).atZone(ZoneOffset.UTC)) + ".jsonl");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, GSON.toJson(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ErrorReporter.report(game, "Failed to append match log " + file, e);
        }
    }
}
