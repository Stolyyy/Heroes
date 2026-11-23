package me.stolyy.heroes.ability.kit.voidcrawler;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class VoidEnergy extends BaseAbility {
    private final float energyPerJab;
    private final double maxDamageDealtMultiplier;

    public VoidEnergy(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        energyPerJab = (float) this.config.getDouble("energy_per_jab");
        maxDamageDealtMultiplier = this.config.getDouble("max_damage_dealt_multiplier");
    }

    @Override
    public void perform(Hero hero) { }

    @Override
    protected void onTick(Hero hero) {
        double energy = hero.getEnergy().getCurrentEnergy();
        double maxEnergy = hero.getEnergy().getMaxEnergy();
        double multiplier = 1 + (maxEnergy > 0 ? (energy / maxEnergy) * (1 - maxDamageDealtMultiplier) : 0);
        hero.getAttributes().setDamageDealtMultiplier(multiplier);
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        hero.getEnergy().changeEnergy(energyPerJab);
    }
}
