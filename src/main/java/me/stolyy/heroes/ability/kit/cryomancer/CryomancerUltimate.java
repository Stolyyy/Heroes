package me.stolyy.heroes.ability.kit.cryomancer;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class CryomancerUltimate extends BaseAbility {
    private boolean isActive = false;

    private final double damageMultiplier;
    private final double knockbackMultiplier;
    private final double cooldownMultiplier;

    private final SoundEffect activationSound;

    private final VisualEffect auraVisual;
    private final int auraInterval;
    private int auraTicks;

    private long originalPrimaryCD;
    private long originalSecondaryCD;

    public CryomancerUltimate(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.damageMultiplier = config.getDouble("damage_multiplier", 0.5);
        this.knockbackMultiplier = config.getDouble("knockback_multiplier", 0.5);
        this.cooldownMultiplier = config.getDouble("cooldown_multiplier", 0.5);
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.auraVisual = new VisualEffect(this.config.getConfigurationSection("visuals.aura_visual"));
        this.auraInterval = this.config.getInt("visuals.aura_interval", 10);
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;

        hero.getAttributes().setDamageMultiplier(damageMultiplier);
        hero.getAttributes().setKnockbackMultiplier(knockbackMultiplier);

        activationSound.play(hero.getController());

        if (hero.getPrimaryAbility() != null) {
            originalPrimaryCD = hero.getPrimaryAbility().getCooldownObject().getCooldownDuration();
            hero.getPrimaryAbility().getCooldownObject().setCooldownDuration((long) (originalPrimaryCD * (cooldownMultiplier)));
        }
        if (hero.getSecondaryAbility() != null) {
            originalSecondaryCD = hero.getSecondaryAbility().getCooldownObject().getCooldownDuration();
            hero.getSecondaryAbility().getCooldownObject().setCooldownDuration((long) (originalSecondaryCD * (cooldownMultiplier)));
        }
    }

    @Override
    protected void onTick(Hero hero) {
        if (!isActive) return;

        if (!getCooldownObject().isInUse()) {
            onExpire(hero, null);
            return;
        }

        if (++auraTicks >= auraInterval) {
            auraTicks = 0;
            auraVisual.play(hero.getController().getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        if (!isActive) return;
        isActive = false;
        hero.getAttributes().resetDamageMultiplier();
        hero.getAttributes().resetKnockbackMultiplier();

        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().getCooldownObject().setCooldownDuration(originalPrimaryCD);
        }
        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().setCooldownDuration(originalSecondaryCD);
        }
    }
}