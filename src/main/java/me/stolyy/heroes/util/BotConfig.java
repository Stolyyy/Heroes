package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Global bot configuration (bots.yml, plugin folder root). Cross-hero knobs live here —
 * task cadence, disabled heroes, recovery/prevention globals, difficulty tiers; per-hero
 * tuning stays in each hero's yml (ai / strategy / recovery sections).
 */
public final class BotConfig {
    private static int taskCheckInterval = 5;
    private static int taskCommitTicks = 10;
    private static final Set<String> disabledHeroes = new HashSet<>();
    private static double recoveryTargetHeightOffset = 2.5;
    private static int movementActionRecheckTicks = 2;
    private static boolean jumpVeto = true;
    private static double reachMargin = 2.0;
    /** Difficulty tier (1-5) -> input delay ticks. Selected per bot in BotSettingsGUI; applied
     *  by Game when the bot's AI is built, overriding the hero yml's ai.input_delay base. */
    private static final Map<Integer, Integer> difficultyInputDelay = new HashMap<>();

    private BotConfig() {
    }

    public static void load() {
        File file = new File(Heroes.getInstance().getDataFolder(), "bots.yml");
        if (!file.exists()) {
            Heroes.getInstance().saveResource("bots.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        taskCheckInterval = config.getInt("task_check_interval", 5);
        taskCommitTicks = config.getInt("task_commit_ticks", 10);
        disabledHeroes.clear();
        for (String name : config.getStringList("disabled_heroes")) {
            disabledHeroes.add(name.toLowerCase(Locale.ROOT));
        }
        recoveryTargetHeightOffset = config.getDouble("recovery.target_height_offset", 2.5);
        movementActionRecheckTicks = config.getInt("movement.action_recheck_ticks", 2);
        jumpVeto = config.getBoolean("prevention.jump_veto", true);
        reachMargin = config.getDouble("prevention.reach_margin", 2.0);

        difficultyInputDelay.clear();
        ConfigurationSection tiers = config.getConfigurationSection("difficulty");
        if (tiers != null) {
            // Numeric tiers 1-5 (V2 §12.2). A non-numeric key is a config error — let it throw.
            for (String tier : tiers.getKeys(false)) {
                difficultyInputDelay.put(Integer.parseInt(tier),
                        tiers.getInt(tier + ".input_delay", 4));
            }
        }
    }

    public static int taskCheckInterval() { return taskCheckInterval; }
    public static int taskCommitTicks() { return taskCommitTicks; }
    public static boolean isDisabledForBots(String heroName) {
        return heroName != null && disabledHeroes.contains(heroName.toLowerCase(Locale.ROOT));
    }
    public static double recoveryTargetHeightOffset() { return recoveryTargetHeightOffset; }
    public static int movementActionRecheckTicks() { return movementActionRecheckTicks; }
    public static boolean isJumpVetoEnabled() { return jumpVeto; }
    public static double preventionReachMargin() { return reachMargin; }
    /** Input delay TICKS for a difficulty tier, or -1 when the tier isn't configured
     *  (caller falls back to the hero yml's ai.input_delay). */
    public static int difficultyInputDelay(int tier) {
        return difficultyInputDelay.getOrDefault(tier, -1);
    }
}
