package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import org.bukkit.configuration.ConfigurationSection;

public class SoulTwister extends Charm {
    private final int costReduction;

    public SoulTwister(ConfigurationSection config) {
        this.costReduction = config != null ? config.getInt("cost_reduction", 8) : 8;
    }

    @Override
    protected void onActivate(Hero hero) {
        Ability secondary = hero.getSecondaryAbility();
        if (secondary != null) {
            secondary.setEnergyCost(secondary.getEnergyCost() - costReduction);
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        Ability secondary = hero.getSecondaryAbility();
        if (secondary != null) {
            secondary.setEnergyCost(secondary.getEnergyCost() + costReduction);
        }
    }
}
