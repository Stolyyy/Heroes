package me.stolyy.heroes.ability.kit.karakot;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class KameDo extends Projectile {
    public KameDo(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (ability.getType() == AbilityType.PRIMARY && Hero.grantsWholeGain(victim)) {
            getCooldownObject().charge(1);
        }
    }
}
