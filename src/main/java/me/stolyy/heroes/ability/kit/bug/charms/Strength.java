package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;

public class Strength extends Charm {
    private final double damageMult;

    public Strength(ConfigurationSection config) {
        this.damageMult = config != null ? config.getDouble("damage_mult", 1.5) : 1.5;
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getCombat().setDamageMultiplier(damageMult);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getCombat().setDamageMultiplier(1.0);
    }
}
