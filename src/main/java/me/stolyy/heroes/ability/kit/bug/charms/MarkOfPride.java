package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;

public class MarkOfPride extends Charm {
    private final double rangeMult;

    public MarkOfPride(ConfigurationSection config) {
        this.rangeMult = config != null ? config.getDouble("range_mult", 1.3) : 1.3;
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getCombat().setRangeMultiplier(rangeMult, hero.getController());
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getCombat().setRangeMultiplier(1.0, hero.getController());
    }
}
