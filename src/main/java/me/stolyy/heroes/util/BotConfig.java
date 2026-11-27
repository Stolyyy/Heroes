package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.HeroConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Global bot configuration ({@code bots.yml}). Cross-hero navigation, evade, recovery, prevention,
 * disabled heroes, and difficulty tiers live here; per-hero tuning stays in each hero YML.
 */
public final class BotConfig {
    private static final Set<String> disabledHeroes = new HashSet<>();
    private static double recoveryTargetHeightOffset;
    private static int movementActionRecheckTicks;
    private static boolean jumpVeto;
    private static double reachMargin;

    private static double navReissueDistanceSq;
    private static double ringSlack;
    private static double backpedalSlack;
    private static double ringHoldDistanceSq;
    private static int navReissueMinTicks;
    private static double velocityQuietSpeedSq;
    private static int jumpCooldownTicks;
    private static double chaseJumpHeightThreshold;

    private static double evadeExitMultiplier;
    private static double evadeFleeMinBlocks;
    private static double evadeFleeMaxBlocks;
    private static int evadeFleeCandidates;
    private static double evadeFleeSpreadDegrees;
    private static double evadeAimDistance;
    private static double evadeAimHeightOffset;

    private static final Map<Integer, Integer> difficultyInputDelay = new HashMap<>();

    private BotConfig() {}

    public static void load() {
        File file = new File(Heroes.getInstance().getDataFolder(), "bots.yml");
        if (!file.exists()) {
            Heroes.getInstance().saveResource("bots.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        disabledHeroes.clear();
        for (String name : config.getStringList("disabled_heroes")) {
            disabledHeroes.add(name.toLowerCase(Locale.ROOT));
        }
        recoveryTargetHeightOffset = Configs.requireDouble(config, "recovery.target_height_offset");
        movementActionRecheckTicks = Configs.requireInt(config, "movement.action_recheck_ticks");
        jumpVeto = Configs.requireBoolean(config, "prevention.jump_veto");
        reachMargin = Configs.requireDouble(config, "prevention.reach_margin");

        double reissueDist = Configs.requireDouble(config, "navigation.reissue_distance");
        navReissueDistanceSq = reissueDist * reissueDist;
        ringSlack = Configs.requireDouble(config, "navigation.ring_slack");
        backpedalSlack = Configs.requireDouble(config, "navigation.backpedal_slack");
        double ringHoldDist = Configs.requireDouble(config, "navigation.ring_hold_distance");
        ringHoldDistanceSq = ringHoldDist * ringHoldDist;
        navReissueMinTicks = Configs.requireInt(config, "navigation.reissue_min_ticks");
        double quietSpeed = Configs.requireDouble(config, "navigation.velocity_quiet_speed");
        velocityQuietSpeedSq = quietSpeed * quietSpeed;
        jumpCooldownTicks = Configs.requireInt(config, "navigation.jump_cooldown_ticks");
        chaseJumpHeightThreshold = Configs.requireDouble(config, "navigation.chase_jump_height_threshold");

        evadeExitMultiplier = Configs.requireDouble(config, "evade.exit_multiplier");
        evadeFleeMinBlocks = Configs.requireDouble(config, "evade.flee_min_blocks");
        evadeFleeMaxBlocks = Configs.requireDouble(config, "evade.flee_max_blocks");
        evadeFleeCandidates = Configs.requireInt(config, "evade.flee_candidates");
        evadeFleeSpreadDegrees = Configs.requireDouble(config, "evade.flee_spread_degrees");
        evadeAimDistance = Configs.requireDouble(config, "evade.aim_distance");
        evadeAimHeightOffset = Configs.requireDouble(config, "evade.aim_height_offset");

        difficultyInputDelay.clear();
        ConfigurationSection tiers = Configs.requireSection(config, "difficulty");
        for (String tier : tiers.getKeys(false)) {
            difficultyInputDelay.put(Integer.parseInt(tier),
                    Configs.requireInt(tiers, tier + ".input_delay"));
        }
    }

    public static boolean isDisabledForBots(String heroName) {
        return heroName != null && disabledHeroes.contains(heroName.toLowerCase(Locale.ROOT));
    }

    public static boolean isEligibleForBots(String heroName) {
        return !isDisabledForBots(heroName);
    }

    public static List<String> eligibleHeroes() {
        List<String> eligible = HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name))
                .filter(BotConfig::isEligibleForBots)
                .toList();
        if (eligible.isEmpty()) {
            throw new IllegalStateException("No heroes eligible for bots (check bots.yml disabled_heroes)");
        }
        return eligible;
    }

    public static double recoveryTargetHeightOffset() { return recoveryTargetHeightOffset; }
    public static int movementActionRecheckTicks() { return movementActionRecheckTicks; }
    public static boolean isJumpVetoEnabled() { return jumpVeto; }
    public static double preventionReachMargin() { return reachMargin; }

    public static double navReissueDistanceSq() { return navReissueDistanceSq; }
    public static double ringSlack() { return ringSlack; }
    public static double backpedalSlack() { return backpedalSlack; }
    public static double ringHoldDistanceSq() { return ringHoldDistanceSq; }
    public static int navReissueMinTicks() { return navReissueMinTicks; }
    public static double velocityQuietSpeedSq() { return velocityQuietSpeedSq; }
    public static int jumpCooldownTicks() { return jumpCooldownTicks; }
    public static double chaseJumpHeightThreshold() { return chaseJumpHeightThreshold; }

    public static double evadeExitMultiplier() { return evadeExitMultiplier; }
    public static double evadeFleeMinBlocks() { return evadeFleeMinBlocks; }
    public static double evadeFleeMaxBlocks() { return evadeFleeMaxBlocks; }
    public static int evadeFleeCandidates() { return evadeFleeCandidates; }
    public static double evadeFleeSpreadDegrees() { return evadeFleeSpreadDegrees; }
    public static double evadeAimDistance() { return evadeAimDistance; }
    public static double evadeAimHeightOffset() { return evadeAimHeightOffset; }

    /** Input delay in ticks for a difficulty tier, or -1 when the tier is not configured. */
    public static int difficultyInputDelay(int tier) {
        return difficultyInputDelay.getOrDefault(tier, -1);
    }
}
