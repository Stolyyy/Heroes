package me.stolyy.heroes.ability.kit.cryomancer;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class CryomancerUltimate extends ActiveAbility {
    private final double damageMultiplier;
    private final double knockbackMultiplier;
    private final double cooldownMultiplier;

    public CryomancerUltimate(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.damageMultiplier = Configs.requireDouble(this.config, "damage_multiplier");
        this.knockbackMultiplier = Configs.requireDouble(this.config, "knockback_multiplier");
        this.cooldownMultiplier = Configs.requireDouble(this.config, "cooldown_multiplier");
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getAttributes().addDamageTakenMultiplier(this, damageMultiplier);
        hero.getAttributes().addKnockbackTakenMultiplier(this, knockbackMultiplier);

        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().getCooldownObject().addDurationMultiplier(this, cooldownMultiplier);
        }
        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().addDurationMultiplier(this, cooldownMultiplier);
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().removeDamageTakenModifier(this);
        hero.getAttributes().removeKnockbackTakenModifier(this);

        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().getCooldownObject().removeDurationModifier(this);
        }
        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().removeDurationModifier(this);
        }
    }
}