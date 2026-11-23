package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class Defensive implements Strategy {
    private final ConfigurationSection config;

    public Defensive(ConfigurationSection config) {
        this.config = config;
    }

    @Override
    public double getPreferredDistance() {
        return config.getDouble("preferred_distance", 8.0);
    }

    @Override
    public boolean shouldUseAbility(Hero hero, AbilityType type, LivingEntity target, double distance) {
        double range = config.getDouble("ability_ranges." + type.name().toLowerCase(), 10.0);
        return distance <= range;
    }

    @Override
    public double getPanicDistance() {
        return config.getDouble("panic_distance", 0.0);
    }

    @Override
    public double getUltMinTargetHpPercent() {
        return config.getDouble("ult_min_target_hp_percent", 0.0);
    }

    @Override
    public double getUltMinSelfHpPercent() {
        return config.getDouble("ult_min_self_hp_percent", 0.0);
    }

    @Override
    public boolean canDoubleJump() {
        return config.getBoolean("can_double_jump", true);
    }

    @Override
    public boolean performMovement(Hero hero, BotController controller, LivingEntity target) {
        // Escape hops live in the EVADE task now (movement.actions aimed away + flee pathing);
        // spacing is BaseBotAI's ring seeking. Nothing jump-worthy remains for Defensive here.
        return false;
    }

}
