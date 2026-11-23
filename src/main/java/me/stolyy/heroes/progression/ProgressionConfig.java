package me.stolyy.heroes.progression;

import me.stolyy.heroes.Heroes;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

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

    public static int getGameReward() { return config.getInt("xp.game-reward", 40); }
    public static int getKillReward() { return config.getInt("xp.kill-reward", 20); }
    public static int getWinReward() { return config.getInt("xp.win-reward", 40); }
    public static int getAssistReward() { return config.getInt("xp.assist-reward", 0); }
    public static double getCustomXpMultiplier() { return config.getDouble("xp.custom-multiplier", 0.5); }

    public static int getMasterSkinLevel() { return config.getInt("levels.master-skin-level", 20); }
    public static int getTokenEveryNLevels() { return config.getInt("levels.token-every-n-levels", 5); }

    public static List<String> getStartingHeroes() { return config.getStringList("unlocks.starting-heroes"); }
    public static int getStartingTokens() { return config.getInt("unlocks.starting-tokens", 2); }
}
