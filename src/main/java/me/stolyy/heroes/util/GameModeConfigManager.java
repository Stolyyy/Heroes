package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.map.MapPool;
import me.stolyy.heroes.game.rules.GameModePreset;
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
    public static int getBossNonBossLives() { return Configs.requireInt(config, "boss.non-boss-lives"); }
    public static double getBossNonBossHp() { return Configs.requireDouble(config, "boss.non-boss-hp"); }
    /** Decimal multiplier: boss lives = round(enemy count x this), min 1 (see BossSetup.bossLives). */
    public static double getBossLivesPerEnemy() { return Configs.requireDouble(config, "boss.boss-lives-per-enemy"); }
    /** Absolute starting/max HP the boss respawns with on every one of its lives. */
    public static double getBossHpPerLife() { return Configs.requireDouble(config, "boss.boss-hp-per-life"); }
    public static boolean getBossUltimatesEnabled() { return Configs.requireBoolean(config, "boss.ultimates-enabled"); }

    // TDM
    public static int getTdmKillLimit() { return Configs.requireInt(config, "tdm.kill-limit"); }
    public static double getTdmStartHp() { return Configs.requireDouble(config, "tdm.start-hp"); }
    public static boolean getTdmChangeHeroOnRespawn() { return Configs.requireBoolean(config, "tdm.change-hero-on-respawn"); }
    public static boolean getTdmUltCarryover() { return Configs.requireBoolean(config, "tdm.ult-carryover"); }

    // Evolution
    public static int getEvolutionKillLimit() { return Configs.requireInt(config, "evolution.kill-limit"); }
    public static double getEvolutionStartHp() { return Configs.requireDouble(config, "evolution.start-hp"); }
    public static boolean getEvolutionRandomizeOrder() { return Configs.requireBoolean(config, "evolution.randomize-order"); }
    public static boolean getEvolutionSharedOrder() { return Configs.requireBoolean(config, "evolution.shared-order"); }
    public static boolean getEvolutionUltCarryover() { return Configs.requireBoolean(config, "evolution.ult-carryover"); }
    public static boolean getEvolutionTeamAdvance() { return Configs.requireBoolean(config, "evolution.team-advance"); }
    public static boolean getEvolutionWinRequiresAll() { return Configs.requireBoolean(config, "evolution.win-requires-all"); }

    /**
     * Per-preset map-pool selector ({@code <preset>.map-pool}). DEFAULT has no pool (null —
     * the custom lobby keeps whatever map it has).
     */
    public static MapPool getMapPool(GameModePreset preset) {
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
        return MapPool.valueOf(
                Configs.requireString(config, key + ".map-pool").trim().toUpperCase().replace('-', '_'));
    }

    // Series (plain best-of-X)
    public static int getSeriesBestOf() { return Configs.requireInt(config, "series.best-of"); }
    public static boolean getSeriesRepickBetweenRounds() { return Configs.requireBoolean(config, "series.repick-between-rounds"); }

    // Nuzlocke
    public static int getNuzlockeStocksPerGame() { return Configs.requireInt(config, "nuzlocke.stocks-per-game"); }
    public static boolean getNuzlockeHpCarryover() { return Configs.requireBoolean(config, "nuzlocke.hp-carryover"); }
    public static boolean getNuzlockeLockIn() { return Configs.requireBoolean(config, "nuzlocke.lockin"); }
    public static String getNuzlockeHeroesToEliminate() { return Configs.requireString(config, "nuzlocke.heroes-to-eliminate"); }
    public static boolean getNuzlockeTeamScope() { return Configs.requireBoolean(config, "nuzlocke.team-scope"); }

    // Ironman
    public static int getIronmanStocksPerGame() { return Configs.requireInt(config, "ironman.stocks-per-game"); }
    public static String getIronmanHeroesToClear() { return Configs.requireString(config, "ironman.heroes-to-clear"); }
    public static boolean getIronmanTeamScope() { return Configs.requireBoolean(config, "ironman.team-scope"); }
}
