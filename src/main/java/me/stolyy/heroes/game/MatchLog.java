package me.stolyy.heroes.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.ErrorReporter;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Appends one JSON line per finished (non-cancelled) game to {@code match-log/YYYY-MM.jsonl} in the
 * plugin data folder. Read offline by {@code tools/matchlog.py}, and caches recent Custom match
 * results in memory for {@link TeamBalancer} session scoring.
 */
public final class MatchLog {

    private record CustomMatchRecord(long endedAt, @Nullable String winner, Map<UUID, String> humanTeams) {}

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final List<CustomMatchRecord> recentCustomGames = new CopyOnWriteArrayList<>();

    private MatchLog() {}

    private static long sessionWindowMs() {
        return Math.round(GameConfigManager.getTeamBalanceSessionWindowHours() * 3_600_000.0);
    }

    /** Seeds the in-memory recent Custom match cache asynchronously from disk at plugin enable. */
    public static void initialize() {
        recentCustomGames.clear();
        long windowMs = sessionWindowMs();
        if (windowMs <= 0) return;

        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;
        Path dir = Heroes.getInstance().getDataFolder().toPath().resolve("match-log");
        Bukkit.getScheduler().runTaskAsynchronously(Heroes.getInstance(), () -> {
            List<CustomMatchRecord> loaded = loadRecentFromDisk(dir, cutoff, now);
            if (!loaded.isEmpty()) {
                recentCustomGames.addAll(loaded);
            }
            pruneExpired(System.currentTimeMillis() - windowMs);
        });
    }

    public static void clear() {
        recentCustomGames.clear();
    }

    private static List<CustomMatchRecord> loadRecentFromDisk(Path dir, long cutoff, long maxEndedAt) {
        List<CustomMatchRecord> loaded = new ArrayList<>();
        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(cutoff).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(maxEndedAt).atZone(ZoneOffset.UTC));
        for (YearMonth ym = startMonth; !ym.isAfter(endMonth); ym = ym.plusMonths(1)) {
            Path file = dir.resolve(ym + ".jsonl");
            if (!Files.exists(file)) continue;
            readMonthFile(file, cutoff, maxEndedAt, loaded);
        }
        return loaded;
    }

    private static void readMonthFile(Path file, long cutoff, long maxEndedAt, List<CustomMatchRecord> out) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String rawLine;
            int lineNum = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNum++;
                if (rawLine.isBlank()) continue;
                try {
                    CustomMatchRecord record = parseCustomRecord(rawLine, cutoff, maxEndedAt);
                    if (record != null) out.add(record);
                } catch (JsonParseException | IllegalStateException | ClassCastException
                        | IllegalArgumentException e) {
                    Heroes.getInstance().getLogger().log(Level.WARNING,
                            "Skipping malformed match log entry in " + file.getFileName() + ":" + lineNum, e);
                }
            }
        } catch (IOException e) {
            Heroes.getInstance().getLogger().log(Level.SEVERE,
                    "Failed to read match log " + file + " for session team balancing", e);
        }
    }

    private static @Nullable CustomMatchRecord parseCustomRecord(String rawLine, long cutoff, long maxEndedAt) {
        JsonObject obj = JsonParser.parseString(rawLine).getAsJsonObject();
        JsonElement modeEl = obj.get("mode");
        if (modeEl == null || modeEl.isJsonNull() || !GameMode.CUSTOM.displayName().equals(modeEl.getAsString())) {
            return null;
        }
        JsonElement endedAtEl = obj.get("endedAt");
        if (endedAtEl == null || !endedAtEl.isJsonPrimitive()) return null;
        long endedAt = endedAtEl.getAsLong();
        if (endedAt < cutoff || endedAt > maxEndedAt) return null;

        JsonElement winnerEl = obj.get("winner");
        String winner = (winnerEl != null && !winnerEl.isJsonNull()) ? winnerEl.getAsString() : null;

        JsonElement participantsEl = obj.get("participants");
        if (participantsEl == null || !participantsEl.isJsonArray()) return null;

        Map<UUID, String> humanTeams = new HashMap<>();
        for (JsonElement element : participantsEl.getAsJsonArray()) {
            JsonObject p = element.getAsJsonObject();
            JsonElement botEl = p.get("isBot");
            if (botEl != null && !botEl.isJsonNull() && botEl.getAsBoolean()) continue;
            JsonElement uuidEl = p.get("uuid");
            JsonElement teamEl = p.get("team");
            if (uuidEl == null || uuidEl.isJsonNull() || teamEl == null || teamEl.isJsonNull()) continue;
            humanTeams.put(UUID.fromString(uuidEl.getAsString()), teamEl.getAsString());
        }
        if (humanTeams.isEmpty()) return null;
        return new CustomMatchRecord(endedAt, winner, Map.copyOf(humanTeams));
    }

    /**
     * Computes session balance scores ({@code wins - losses}) for {@code lobbyHumans} over recent
     * Custom games within the rolling window where strictly more than half of the match's human
     * participants are present in {@code lobbyHumans}. Draws contribute 0.
     */
    public static Map<UUID, Integer> sessionScores(Set<UUID> lobbyHumans) {
        long windowMs = sessionWindowMs();
        if (windowMs <= 0 || lobbyHumans.isEmpty()) return Map.of();

        long cutoff = System.currentTimeMillis() - windowMs;
        pruneExpired(cutoff);

        Map<UUID, Integer> scores = new HashMap<>();
        for (CustomMatchRecord match : recentCustomGames) {
            if (match.endedAt() < cutoff || match.winner() == null) continue;
            int humanCount = match.humanTeams().size();
            if (humanCount == 0) continue;

            int overlap = 0;
            for (UUID participant : match.humanTeams().keySet()) {
                if (lobbyHumans.contains(participant)) overlap++;
            }
            if (overlap * 2 <= humanCount) continue;

            for (Map.Entry<UUID, String> entry : match.humanTeams().entrySet()) {
                UUID participant = entry.getKey();
                if (!lobbyHumans.contains(participant)) continue;
                int delta = entry.getValue().equals(match.winner()) ? 1 : -1;
                scores.merge(participant, delta, Integer::sum);
            }
        }
        return scores;
    }

    private static void pruneExpired(long cutoff) {
        recentCustomGames.removeIf(match -> match.endedAt() < cutoff);
    }

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

        boolean trackCustom = game.gameMode() == GameMode.CUSTOM && sessionWindowMs() > 0;
        Map<UUID, String> humanTeams = trackCustom ? new HashMap<>() : null;

        JsonArray participants = new JsonArray();
        for (Map.Entry<UUID, GamePlayer> entry : game.getGamePlayers().entrySet()) {
            GamePlayer gamePlayer = entry.getValue();
            TeamColor team = game.originalColor(entry.getKey());
            if (team == null || team == TeamColor.SPECTATOR) continue;
            if (humanTeams != null && !gamePlayer.isBot()) {
                humanTeams.put(entry.getKey(), team.name());
            }
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

        if (humanTeams != null && !humanTeams.isEmpty()) {
            recentCustomGames.add(new CustomMatchRecord(
                    endedAt,
                    winner == null ? null : winner.color().name(),
                    Map.copyOf(humanTeams)));
            pruneExpired(endedAt - sessionWindowMs());
        }

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
