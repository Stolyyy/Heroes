package me.stolyy.heroes.ability.kit.pug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Cone;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class PugBork extends Cone {
    private final double minKnockback;

    public PugBork(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.minKnockback = Configs.requireDouble(this.config, "min_knockback");
    }

    @Override
    protected double calcKnockback(Hero hero, LivingEntity victim) {
        double dist = hero.getController().getLocation().distance(victim.getLocation());
        double kbRange = knockback - minKnockback;
        double finalKb = knockback - (kbRange * (dist / range));
        return Math.max(minKnockback, finalKb);
    }
}
