package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;

public class SteadyBody extends Charm {
    private final double weightMult;

    public SteadyBody(ConfigurationSection config) {
        this.weightMult = config != null ? config.getDouble("weight_mult", 2.25) : 2.25;
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getAttributes().setWeight(hero.getAttributes().getWeight() * weightMult);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().setWeight(hero.getAttributes().getWeight() / weightMult);
    }
}
