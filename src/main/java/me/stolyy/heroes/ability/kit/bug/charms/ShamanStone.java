package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import org.bukkit.configuration.ConfigurationSection;

public class ShamanStone extends Charm {
    private final double damageMult;
    private final double knockbackMult;
    private final double radiusAdd;

    public ShamanStone(ConfigurationSection config) {
        this.damageMult = config != null ? config.getDouble("damage_mult", 1.3) : 1.3;
        this.knockbackMult = config != null ? config.getDouble("kb_mult", 1.3) : 1.3;
        this.radiusAdd = config != null ? config.getDouble("radius_add", 1.5) : 1.5;
    }

    @Override
    protected void onActivate(Hero hero) {
        Ability secondary = hero.getSecondaryAbility();
        if (secondary != null) {
            secondary.setDamage(secondary.getDamage() * damageMult);
            secondary.setKnockback(secondary.getKnockback() * knockbackMult);
            secondary.setRadius(secondary.getRadius() + radiusAdd);
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        Ability secondary = hero.getSecondaryAbility();
        if (secondary != null) {
            secondary.setDamage(secondary.getDamage() / damageMult);
            secondary.setKnockback(secondary.getKnockback() / knockbackMult);
            secondary.setRadius(secondary.getRadius() - radiusAdd);
        }
    }
}
