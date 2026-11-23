package me.stolyy.heroes.ability.kit.tinman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class RocketPunch extends Projectile {
    public RocketPunch(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (ability.getType() == AbilityType.PRIMARY && Hero.grantsWholeGain(victim)) {
            getCooldownObject().charge(1);
        }
    }
}
