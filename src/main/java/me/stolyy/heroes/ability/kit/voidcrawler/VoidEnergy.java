package me.stolyy.heroes.ability.kit.voidcrawler;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class VoidEnergy extends BaseAbility {
    private final float energyPerJab;
    private final double maxDamageDealtMultiplier;

    public VoidEnergy(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        energyPerJab = (float) Configs.requireDouble(this.config, "energy_per_jab");
        maxDamageDealtMultiplier = Configs.requireDouble(this.config, "max_damage_dealt_multiplier");
    }

    @Override
    public void perform(Hero hero) { }

    @Override
    protected void onTick(Hero hero) {
        double energy = hero.getEnergy().getCurrentEnergy();
        double maxEnergy = hero.getEnergy().getMaxEnergy();
        double multiplier = 1 + (maxEnergy > 0 ? (energy / maxEnergy) * (maxDamageDealtMultiplier - 1) : 0);
        hero.getAttributes().addDamageDealtMultiplier(this, multiplier);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        hero.getAttributes().removeDamageDealtModifier(this);
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        hero.getEnergy().changeEnergy(energyPerJab);
    }
}
