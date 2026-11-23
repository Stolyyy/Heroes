package me.stolyy.heroes.ability.kit.railgunner;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class HeadHunter extends BaseAbility {
    private final double damageDealtMultiplier;
    private boolean headshot = false;

    public HeadHunter(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.damageDealtMultiplier = Configs.requireDouble(config, "damage_dealt_multiplier");
    }

    @Override
    public void perform(Hero hero) {

    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        super.onAbilityHit(hero, ability, victim);

        if(ability.getType() != AbilityType.PRIMARY || !headshot) {
            setDamageMultiplier(hero, 1.0);
        }
        else {
            setDamageMultiplier(hero, damageDealtMultiplier);
        }
        
        setHeadshot(false);
    }
    
    public void setHeadshot(boolean headshot) {
        this.headshot = headshot;
    }

    private void setDamageMultiplier(Hero hero, double multiplier) {
        hero.getAttributes().setDamageDealtMultiplier(multiplier);
    }
}
