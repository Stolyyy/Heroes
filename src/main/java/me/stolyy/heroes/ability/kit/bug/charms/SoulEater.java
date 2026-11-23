package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;

public class SoulEater extends Charm {
    private final double multiplier;

    public SoulEater(ConfigurationSection config) {
        this.multiplier = config != null ? config.getDouble("multiplier", 1.75) : 1.75;
    }

    public double getMultiplier() {
        return multiplier;
    }

    @Override
    protected void onActivate(Hero hero) {}

    @Override
    protected void onDeactivate(Hero hero) {}
}
