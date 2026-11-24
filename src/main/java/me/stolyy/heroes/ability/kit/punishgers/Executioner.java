package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class Executioner extends ActiveAbility {
    private final double fireRateMultiplier;
    private final double grenadeCooldownMultiplier;

    private PunishgersWeapon weapon;

    public Executioner(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.fireRateMultiplier = Configs.requireDouble(this.config, "fire_rate_multiplier");
        this.grenadeCooldownMultiplier = Configs.requireDouble(this.config, "grenade_cooldown_multiplier");
    }

    @Override
    public void initialize(Hero hero) {
        this.weapon = hero.findAbility(PunishgersWeapon.class).orElse(null);
    }

    @Override
    protected void onActivate(Hero hero) {
        if (weapon != null) {
            weapon.setUnlimitedAmmo(true);
            weapon.setPenaltyDisabled(hero, true);
            weapon.addFireRateModifier(this, fireRateMultiplier);
        }
        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().addDurationMultiplier(this, grenadeCooldownMultiplier);
        }
    }

    @Override
    protected void onActiveExpired(Hero hero) {
        if (weapon != null) weapon.drainClips(hero);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        if (weapon != null) {
            weapon.setUnlimitedAmmo(false);
            weapon.setPenaltyDisabled(hero, false);
            weapon.removeFireRateModifier(this);
        }
        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().removeDurationModifier(this);
        }
    }
}
