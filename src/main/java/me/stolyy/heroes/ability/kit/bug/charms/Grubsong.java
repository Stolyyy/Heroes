package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;

public class Grubsong extends Charm {
    private final double soulPerDamage;

    public Grubsong(ConfigurationSection config) {
        this.soulPerDamage = config != null ? config.getDouble("soul_per_damage", 17.0) : 17.0;
    }

    @Override
    protected void onActivate(Hero hero) {}

    @Override
    protected void onDeactivate(Hero hero) {}

    @Override
    public void onDamageTaken(Hero hero, Entity source, double amount) {
        hero.getEnergy().changeEnergy((float) (amount * soulPerDamage));
    }
}
