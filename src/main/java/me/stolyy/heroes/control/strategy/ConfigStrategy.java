package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;

/**
 * Shared {@link Strategy} base reading per-hero config keys with {@code templates.yml} fallbacks.
 */
public abstract class ConfigStrategy implements Strategy {
    protected final ConfigurationSection config;
    private final String templateKey;
    private final boolean supportsPanic;

    protected ConfigStrategy(ConfigurationSection config, String templateKey, boolean supportsPanic) {
        this.config = Objects.requireNonNull(config, "strategy config must not be null");
        this.templateKey = Objects.requireNonNull(templateKey, "templateKey must not be null");
        this.supportsPanic = supportsPanic;
    }

    @Override
    public double getPreferredDistance() {
        return Configs.requireDouble(config, "preferred_distance");
    }

    @Override
    public boolean shouldUseAbility(Hero hero, AbilityType type, LivingEntity target, double distance) {
        double range = Configs.orTemplate(config, "ability_ranges." + type.name().toLowerCase(),
                "bot.strategy." + templateKey + ".ability_range");
        return distance <= range;
    }

    @Override
    public double getPanicDistance() {
        if (!supportsPanic) return 0.0;
        return Configs.orTemplate(config, "panic_distance", "bot.strategy." + templateKey + ".panic_distance");
    }

    @Override
    public double getUltMinTargetHpPercent() {
        return Configs.orTemplate(config, "ult_min_target_hp_percent", "bot.strategy.ult_min_target_hp_percent");
    }

    @Override
    public double getUltMinSelfHpPercent() {
        return Configs.orTemplate(config, "ult_min_self_hp_percent", "bot.strategy.ult_min_self_hp_percent");
    }

    @Override
    public double getFarDistance() {
        return Configs.orTemplate(config, "far_distance", "bot.strategy." + templateKey + ".far_distance");
    }

    protected boolean canDoubleJump() {
        return Configs.requireBoolean(config, "can_double_jump");
    }

    /** Faces {@code target} before jumping so double-jump velocity aims toward them if safe and unobstructed. */
    protected boolean tryChaseJump(Hero hero, BotController controller, LivingEntity target) {
        if (!JumpSafety.isChaseJumpSafe(hero, controller, target)) return false;
        if (!JumpSafety.hasHeadroom(hero, controller)) return false;
        controller.lookAt(target.getEyeLocation());
        hero.getMobility().performJump(hero);
        return true;
    }
}
