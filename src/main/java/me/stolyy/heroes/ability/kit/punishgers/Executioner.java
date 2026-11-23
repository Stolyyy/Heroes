package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class Executioner extends BaseAbility {
    private final double fireRateMultiplier;
    private final double grenadeCooldownMultiplier;
    private final SoundEffect activationSound;

    private PunishgersWeapon weapon;
    private boolean isActive = false;

    public Executioner(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.fireRateMultiplier = this.config.getDouble("fire_rate_multiplier");
        this.grenadeCooldownMultiplier = this.config.getDouble("grenade_cooldown_multiplier");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void initialize(Hero hero) {
        if (hero.getPrimaryAbility() instanceof PunishgersWeapon w) {
            this.weapon = w;
        }
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;
        activationSound.play(hero.getController().getLocation(), hero.getController());

        if (weapon != null) {
            weapon.setUnlimitedAmmo(true);
            weapon.setPenaltyDisabled(hero, true);
            weapon.applyFireRateMultiplier(fireRateMultiplier);
        }
        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().setCooldownMultiplier(grenadeCooldownMultiplier);
        }
    }

    @Override
    protected void onTick(Hero hero) {
        if (!isActive) return;
        if (!getCooldownObject().isInUse()) {
            if (weapon != null) weapon.drainClips(hero);
            onExpire(hero, null);
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        if (!isActive) return;
        isActive = false;

        if (weapon != null) {
            weapon.setUnlimitedAmmo(false);
            weapon.setPenaltyDisabled(hero, false);
            weapon.applyFireRateMultiplier(1.0);
        }
        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().setCooldownMultiplier(1.0);
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        onExpire(hero, null);
    }
}
