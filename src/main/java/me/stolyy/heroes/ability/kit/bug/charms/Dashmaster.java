package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import org.bukkit.configuration.ConfigurationSection;

public class Dashmaster extends Charm {
    private final double cdMult;

    public Dashmaster(ConfigurationSection config) {
        this.cdMult = config != null ? config.getDouble("cd_mult", 0.7) : 0.7;
    }

    @Override
    protected void onActivate(Hero hero) {
        Ability primary = hero.getPrimaryAbility();
        if (primary != null) {
            primary.setCooldown((long) (primary.getCooldown() * cdMult));
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        Ability primary = hero.getPrimaryAbility();
        if (primary != null) {
            primary.setCooldown((long) (primary.getCooldown() / cdMult));
        }
    }
}
