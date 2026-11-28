package me.stolyy.heroes.progression;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Loads progression.yml — XP curve, reward amounts, token cadence, master-skin
 * threshold, and the starting unlock roster/tokens.
 */
public class ProgressionConfig {
    private static FileConfiguration config;
    private static List<Integer> xpCurve;

    public static void load() {
        Heroes plugin = Heroes.getInstance();
        File file = new File(plugin.getDataFolder(), "progression.yml");
        if (!file.exists()) {
            plugin.saveResource("progression.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        xpCurve = config.getIntegerList("xp.curve");
    }

    /**
     * XP required to advance FROM {@code level} to the next one. Past the end of the
     * curve returns MAX_VALUE — the curve's length IS the level cap.
     */
    public static int getXpForLevel(int level) {
        if (level < 0 || level >= xpCurve.size()) return Integer.MAX_VALUE;
        return xpCurve.get(level);
    }

    public static int getGameReward() { return Configs.requireInt(config, "xp.game-reward"); }
    public static int getKillReward() { return Configs.requireInt(config, "xp.kill-reward"); }
    public static int getWinReward() { return Configs.requireInt(config, "xp.win-reward"); }
    public static int getAssistReward() { return Configs.requireInt(config, "xp.assist-reward"); }
    public static double getCustomXpMultiplier() { return Configs.requireDouble(config, "xp.custom-multiplier"); }

    public static int getMasterSkinLevel() { return Configs.requireInt(config, "levels.master-skin-level"); }
    public static int getTokenEveryNLevels() { return Configs.requireInt(config, "levels.token-every-n-levels"); }

    public static List<String> getStartingHeroes() { return config.getStringList("unlocks.starting-heroes"); }
    public static int getStartingTokens() { return Configs.requireInt(config, "unlocks.starting-tokens"); }

    public static boolean isShardsEnabled() { return Configs.requireBoolean(config, "shards.enabled"); }
    public static double getShardBaseChance() { return Configs.requireDouble(config, "shards.base-chance"); }
    public static double getShardPityStep() { return Configs.requireDouble(config, "shards.pity-step"); }
    public static int getShardRarityWeight(HeroSkin.Rarity rarity) {
        return Configs.requireInt(config, "shards.rarity-weights." + rarity.name().toLowerCase(Locale.ROOT));
    }
}
