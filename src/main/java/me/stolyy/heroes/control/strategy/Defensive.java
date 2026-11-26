package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/** Ranged kiting strategy; spacing and evasion are handled by navigation and evade tasks. */
public class Defensive extends ConfigStrategy {

    public Defensive(ConfigurationSection config) {
        super(config, "defensive", true);
    }

    @Override
    public boolean performMovement(Hero hero, BotController controller, LivingEntity target) {
        return false;
    }
}
