package me.stolyy.heroes.ability.kit.cakemonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class GoodBaker extends BaseAbility {
    public GoodBaker(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    public void perform(Hero hero) {
        hero.getPrimaryAbility().getCooldownObject().forceReady();
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        super.onAbilityHit(hero, ability, victim);

        if (ability.getType() == AbilityType.PRIMARY) return;

        perform(hero);
    }
}
