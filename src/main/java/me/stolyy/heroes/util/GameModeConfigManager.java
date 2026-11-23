package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Loads gamemodes.yml — the single config home for gamemode preset defaults
 * (Boss, TDM, Evolution, Nuzlocke, Ironman). Presets read these values when their
 * GameModeConfig is built; per-game tweaks happen in the GUIs afterward.
 */
public class GameModeConfigManager {
    private static FileConfiguration config;

    public static void reload() {
        Heroes plugin = Heroes.getInstance();
        File file = new File(plugin.getDataFolder(), "gamemodes.yml");
        if (!file.exists()) {
            plugin.saveResource("gamemodes.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    // Boss
    public static int getBossNonBossLives() { return config.getInt("boss.non-boss-lives", 1); }
    public static double getBossNonBossHp() { return config.getDouble("boss.non-boss-hp", 100.0); }
    /** Decimal multiplier: boss lives = round(enemy count x this), min 1 (see BossSetup.bossLives). */
    public static double getBossLivesPerEnemy() { return config.getDouble("boss.boss-lives-per-enemy", 1.0); }
    /** Absolute starting/max HP the boss respawns with on every one of its lives. */
    public static double getBossHpPerLife() { return config.getDouble("boss.boss-hp-per-life", 100.0); }
    public static boolean getBossUltimatesEnabled() { return config.getBoolean("boss.ultimates-enabled", true); }

    // TDM
    public static int getTdmKillLimit() { return config.getInt("tdm.kill-limit", 20); }
    public static double getTdmStartHp() { return config.getDouble("tdm.start-hp", 50.0); }
    public static boolean getTdmChangeHeroOnRespawn() { return config.getBoolean("tdm.change-hero-on-respawn", true); }
    public static boolean getTdmUltCarryover() { return config.getBoolean("tdm.ult-carryover", true); }

    // Evolution
    public static int getEvolutionKillLimit() { return config.getInt("evolution.kill-limit", 0); }
    public static double getEvolutionStartHp() { return config.getDouble("evolution.start-hp", 50.0); }
    public static boolean getEvolutionRandomizeOrder() { return config.getBoolean("evolution.randomize-order", true); }
    public static boolean getEvolutionSharedOrder() { return config.getBoolean("evolution.shared-order", false); }
    public static boolean getEvolutionUltCarryover() { return config.getBoolean("evolution.ult-carryover", true); }
    public static boolean getEvolutionTeamAdvance() { return config.getBoolean("evolution.team-advance", false); }
    public static boolean getEvolutionWinRequiresAll() { return config.getBoolean("evolution.win-requires-all", false); }

    /**
     * Per-preset map-pool selector ({@code <preset>.map-pool}). DEFAULT has no pool (null —
     * the custom lobby keeps whatever map it has); series-family presets default to BOTH
     * (maps supporting 1v1 AND 2v2), the rest to ALL.
     */
    public static me.stolyy.heroes.game.map.MapPool getMapPool(me.stolyy.heroes.game.rules.GameModePreset preset) {
        String key = switch (preset) {
            case DEFAULT -> null;
            case BOSS -> "boss";
            case TDM -> "tdm";
            case EVOLUTION -> "evolution";
            case SERIES -> "series";
            case NUZLOCKE -> "nuzlocke";
            case IRONMAN -> "ironman";
        };
        if (key == null) return null;
        String fallback = preset.isSeries() ? "BOTH" : "ALL";
        return me.stolyy.heroes.game.map.MapPool.valueOf(
                config.getString(key + ".map-pool", fallback).trim().toUpperCase().replace('-', '_'));
    }

    // Series (plain best-of-X)
    public static int getSeriesBestOf() { return config.getInt("series.best-of", 3); }
    public static boolean getSeriesRepickBetweenRounds() { return config.getBoolean("series.repick-between-rounds", true); }

    // Nuzlocke
    public static int getNuzlockeStocksPerGame() { return config.getInt("nuzlocke.stocks-per-game", 1); }
    public static boolean getNuzlockeHpCarryover() { return config.getBoolean("nuzlocke.hp-carryover", true); }
    public static boolean getNuzlockeLockIn() { return config.getBoolean("nuzlocke.lockin", true); }
    public static String getNuzlockeHeroesToEliminate() { return config.getString("nuzlocke.heroes-to-eliminate", "all"); }
    public static boolean getNuzlockeTeamScope() { return config.getBoolean("nuzlocke.team-scope", false); }

    // Ironman
    public static int getIronmanStocksPerGame() { return config.getInt("ironman.stocks-per-game", 1); }
    public static String getIronmanHeroesToClear() { return config.getString("ironman.heroes-to-clear", "all"); }
    public static boolean getIronmanTeamScope() { return config.getBoolean("ironman.team-scope", false); }
}
