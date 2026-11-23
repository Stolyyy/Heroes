package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;

public class QuickSlash extends Charm {
    private final double cdMult;

    public QuickSlash(ConfigurationSection config) {
        this.cdMult = config != null ? config.getDouble("cd_mult", 0.7) : 0.7;
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getCombat().setCooldownMultiplier(cdMult);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getCombat().setCooldownMultiplier(1.0);
    }
}
