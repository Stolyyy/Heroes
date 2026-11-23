package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;

public class Kingsoul extends Charm {
    private final float regenPerTick;

    public Kingsoul(ConfigurationSection config) {
        double perSecond = config != null ? config.getDouble("regen_per_second", 6.0) : 6.0;
        this.regenPerTick = (float) (perSecond / 20.0);
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getEnergy().setRegenPerTick(hero.getEnergy().getRegenPerTick() + regenPerTick);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getEnergy().setRegenPerTick(hero.getEnergy().getRegenPerTick() - regenPerTick);
    }
}
