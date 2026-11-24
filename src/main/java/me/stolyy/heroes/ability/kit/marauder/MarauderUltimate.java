package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class MarauderUltimate extends ActiveAbility {
    private final double damageMitigation;
    private final float energyRegenMultiplier;

    public MarauderUltimate(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.damageMitigation = Configs.requireDouble(this.config, "damage_mitigation");
        this.energyRegenMultiplier = (float) Configs.requireDouble(this.config, "energy_regen_multiplier");
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getAttributes().addDamageTakenMultiplier(this, damageMitigation);
        hero.getEnergy().addRegenMultiplier(this, energyRegenMultiplier);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().removeDamageTakenModifier(this);
        hero.getEnergy().removeRegenModifier(this);
    }
}