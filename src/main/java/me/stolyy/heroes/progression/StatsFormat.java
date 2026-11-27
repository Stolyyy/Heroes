package me.stolyy.heroes.progression;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStatsManager.LeaderboardEntry;
import me.stolyy.heroes.progression.PlayerStatsManager.Stat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared formatting and leaderboard query helpers used by both in-game commands
 * ({@code StatsCommand}, {@code LeaderboardCommand}) and Discord bot commands so their numbers
 * and labels never drift.
 */
public final class StatsFormat {
    private StatsFormat() {}

    public static String displayName(PlayerStats stats, String fallback) {
        return stats.lastKnownName() != null ? stats.lastKnownName() : fallback;
    }

    public static String round(double value) {
        return String.valueOf(Math.round(value));
    }

    public static String kd(int kills, int deaths) {
        return deaths > 0 ? String.format(Locale.ROOT, "%.2f", kills / (double) deaths) : "∞";
    }

    public static String record(int wins, int losses, int kills, int deaths) {
        return wins + "W-" + losses + "L   K/D: " + kills + "/" + deaths + " (" + kd(kills, deaths) + ")";
    }

    public static String damage(double damage, double voidDamage) {
        return round(damage) + " (void " + round(voidDamage) + ")";
    }

    public static String heroLevel(HeroLevel level) {
        return level.level() + " (" + level.xp() + " xp into level)";
    }

    public static String mostPlayed(PlayerStats stats, int limit) {
        return stats.gamesPerHero().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> HeroConfig.formatName(e.getKey()) + " (" + e.getValue() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("none yet");
    }

    public static String modeHeroes(ModeStats modeStats) {
        return modeStats.heroStats().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, HeroProgress> e)
                        -> e.getValue().wins() + e.getValue().losses()).reversed())
                .map(e -> HeroConfig.formatName(e.getKey()) + " "
                        + e.getValue().wins() + "W-" + e.getValue().losses() + "L")
                .reduce((a, b) -> a + " | " + b)
                .orElse("none yet");
    }

    public static String heroPerMode(PlayerStats stats, String hero) {
        List<String> perMode = new ArrayList<>();
        for (GameMode m : GameMode.values()) {
            HeroProgress progress = stats.findHeroStats(m, hero);
            if (progress == null || progress.wins() + progress.losses() == 0) continue;
            perMode.add(m.displayName() + " " + progress.wins() + "W-" + progress.losses() + "L");
        }
        return perMode.isEmpty() ? "none yet" : String.join(" | ", perMode);
    }

    public static String topAbilities(PlayerStats.HeroSummary summary, int limit) {
        return summary.abilities().entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, AbilityStats> e)
                        -> e.getValue().damage()).reversed())
                .limit(limit)
                .map(e -> e.getKey() + " " + round(e.getValue().damage()) + " dmg ("
                        + e.getValue().kills() + " kills)")
                .reduce((a, b) -> a + ", " + b)
                .orElse("none yet");
    }

    public static List<LeaderboardEntry> queryLeaderboard(boolean levelBoard, @Nullable String hero,
                                                          @Nullable Stat stat, @Nullable GameMode mode, int limit) {
        if (levelBoard) return PlayerStatsManager.getTopByLevel(hero, limit);
        if (stat != null) return PlayerStatsManager.getTopByStat(stat, mode, limit);
        return PlayerStatsManager.getTopPlayers(mode, limit);
    }

    public static String leaderboardTitle(boolean levelBoard, @Nullable String hero,
                                          @Nullable Stat stat, @Nullable GameMode mode) {
        if (levelBoard) return "Level Leaderboard (" + (hero == null ? "Overall" : HeroConfig.formatName(hero)) + ")";
        if (stat != null) return stat.displayName() + " Leaderboard (" + (mode == null ? "All Modes" : mode.displayName()) + ")";
        return "ELO Leaderboard (" + mode.displayName() + ")";
    }

    public static String leaderboardValue(boolean levelBoard, int value) {
        return levelBoard ? "Lv. " + value : String.valueOf(value);
    }
}
