package me.stolyy.heroes.ability.kit.cryomancer;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class CryomancerFrostAura extends BaseAbility {
    private final double radius;
    private final double extraDamage;
    private double initialDamage;

    public CryomancerFrostAura(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.radius = config.getDouble("radius");
        this.extraDamage = config.getDouble("extra_damage");
    }

    @Override
    public void perform(Hero hero) { }

    @Override
    public void initialize(Hero hero){
        initialDamage = hero.getPrimaryAbility().getDamage();
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (ability.getType() != AbilityType.PRIMARY) return;

        double distance = hero.getController().getLocation().distance(victim.getLocation());
        if (distance <= radius) {
            ability.setDamage(initialDamage + extraDamage);
        } else {
            ability.setDamage(initialDamage);
        }
    }
}