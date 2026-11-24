package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

/**
 * Combat spacing, ability range, ult gating, and movement heuristics for bot heroes and minions.
 */
public interface Strategy {

    /** Target spacing radius around the enemy used by {@code BotNavigation}. */
    double getPreferredDistance();

    /** Melee/hybrid proximity threshold that triggers {@code EVADE}, or 0 to disable. */
    double getPanicDistance();

    boolean shouldUseAbility(Hero hero, AbilityType type, LivingEntity target, double distance);

    /** Minimum target HP fraction (0..1) required to cast smash (0 = no minimum). */
    double getUltMinTargetHpPercent();

    /** Minimum self HP fraction (0..1) required to cast smash unless on last life (0 = no minimum). */
    double getUltMinSelfHpPercent();

    /** Horizontal distance beyond which chase jumps and gap-closers engage. */
    double getFarDistance();

    /** Performs strategy-specific chase jumps when fighting a target. */
    boolean performMovement(Hero hero, BotController controller, LivingEntity target);

    static Strategy fromConfig(ConfigurationSection rootConfig) {
        String strategyType = Configs.requireString(rootConfig, "bot.strategy_type");
        ConfigurationSection strategyCfg = Configs.requireSection(rootConfig, "strategy");
        return fromType(strategyType, strategyCfg);
    }

    static Strategy fromType(String type, ConfigurationSection strategyConfig) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "AGGRESSIVE" -> new Aggressive(strategyConfig);
            case "DEFENSIVE" -> new Defensive(strategyConfig);
            case "MIDRANGE" -> new MidRange(strategyConfig);
            default -> throw new IllegalStateException("Unknown bot.strategy_type '" + type + "'");
        };
    }

    static boolean isKnownType(String type) {
        if (type == null) return false;
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "AGGRESSIVE", "DEFENSIVE", "MIDRANGE" -> true;
            default -> false;
        };
    }

    static double horizontalDistance(Location a, Location b) {
        return Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
    }
}
