package me.stolyy.heroes.ability.kit.mrmonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class MonsterRage extends BaseAbility {
    private final double energyPerHit;
    private final double energyPerDamage;
    private final double maxDamageDealtMultiplier;
    private final double maxKnockbackDealtMultiplier;

    public MonsterRage(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.energyPerHit = Configs.requireDouble(this.config, "energy_per_hit");
        this.energyPerDamage = Configs.requireDouble(this.config, "energy_per_damage");
        this.maxDamageDealtMultiplier = Configs.requireDouble(this.config, "max_damage_dealt_multiplier");
        this.maxKnockbackDealtMultiplier = Configs.requireDouble(this.config, "max_knockback_dealt_multiplier");
    }

    @Override
    public void initialize(Hero hero) {
        hero.getEventBus().onDamageTaken((source, amount) ->
                hero.getEnergy().changeEnergy((float) (energyPerHit + amount * energyPerDamage)));
    }

    @Override
    public void perform(Hero hero) {
    }

    @Override
    protected void onTick(Hero hero) {
        int max = hero.getEnergy().getMaxEnergy();
        double fraction = max > 0 ? hero.getEnergy().getCurrentEnergy() / (double) max : 0;
        hero.getAttributes().setDamageDealtMultiplier(1 + fraction * (maxDamageDealtMultiplier - 1));
        hero.getAttributes().setKnockbackDealtMultiplier(1 + fraction * (maxKnockbackDealtMultiplier - 1));
    }
}
