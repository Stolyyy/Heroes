package me.stolyy.heroes.ability.kit.bulk;

import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.CompositeAbility;
import me.stolyy.heroes.ability.template.Shockwave;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BulkSmash extends CompositeAbility {
    private final int smashCount;

    private final int launchDelayTicks;
    private final double launchHorizontalStrength;
    private final double launchVerticalStrength;
    private final int descentDelayTicks;
    private final double descentHorizontalStrength;
    private final double descentVerticalStrength;

    private final double secondaryCooldownMultiplier;

    private final Shockwave landingShockwave;

    private final SoundEffect activationSound;
    private final SoundEffect launchSound;
    private final SoundEffect descentSound;
    private final SoundEffect hitSound;
    private final VisualEffect launchVisual;
    private final VisualEffect descentVisual;
    private final VisualEffect hitVisual;

    private boolean isActive = false;
    private int smashesRemaining = 0;
    private boolean waitingForLaunch = false;
    private int launchDelayCounter = 0;
    private boolean isDescending = false;
    private boolean waitingForDescent = false;
    private int descentDelayCounter = 0;
    private final Map<UUID, Long> descentHitHistory = new HashMap<>();

    public BulkSmash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.smashCount = Configs.requireInt(this.config, "smash_count");
        this.launchDelayTicks = Configs.requireInt(this.config, "launch_delay_ticks");
        this.launchHorizontalStrength = Configs.requireDouble(this.config, "launch_horizontal_strength");
        this.launchVerticalStrength = Configs.requireDouble(this.config, "launch_vertical_strength");
        this.descentDelayTicks = Configs.requireInt(this.config, "descent_delay_ticks");
        this.descentHorizontalStrength = Configs.requireDouble(this.config, "descent_horizontal_strength");
        this.descentVerticalStrength = Configs.requireDouble(this.config, "descent_vertical_strength");

        this.secondaryCooldownMultiplier = Configs.requireDouble(this.config, "secondary_cooldown_multiplier");

        this.landingShockwave = addChild(new Shockwave(
                Configs.requireSection(this.config, "shockwave"), skin, AbilityType.SMASH));

        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
        this.launchSound = SoundEffect.optional(this.config, "visuals.launch_sound");
        this.descentSound = SoundEffect.optional(this.config, "visuals.descent_sound");
        this.hitSound = new SoundEffect(Configs.requireSection(this.config, "visuals.hit_sound"));
        this.launchVisual = VisualEffect.optional(this.config, "visuals.launch_visual");
        this.descentVisual = VisualEffect.optional(this.config, "visuals.descent_visual");
        this.hitVisual = VisualEffect.optional(this.config, "visuals.hit_visual");
    }

    @Override
    protected void onCompositeDeath(Hero hero) {
        endUltimate(hero);
    }

    @Override
    protected void onCompositeOwnerLost(Hero hero) {
        endUltimate(hero);
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;
        smashesRemaining = smashCount;
        descentHitHistory.clear();

        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().addDurationMultiplier(this, secondaryCooldownMultiplier);
        }

        activationSound.play(hero.getController());

        performSmash(hero);
    }

    private void performSmash(Hero hero) {
        if (smashesRemaining <= 0 || !isActive) return;

        smashesRemaining--;
        isDescending = false;
        descentHitHistory.clear();

        waitingForLaunch = true;
        launchDelayCounter = launchDelayTicks;
    }

    private void beginLaunch(Hero hero) {
        Location loc = hero.getController().getLocation();
        Vector direction = loc.getDirection().normalize();

        Vector launchVelocity = direction.clone().multiply(launchHorizontalStrength);
        launchVelocity.setY(launchVerticalStrength);
        hero.getController().setVelocity(launchVelocity);

        launchSound.play(loc, hero.getController());
        launchVisual.play(loc, direction);

        waitingForDescent = true;
        descentDelayCounter = descentDelayTicks;
    }

    private void beginDescent(Hero hero) {
        isDescending = true;

        Location loc = hero.getController().getLocation();
        Vector direction = loc.getDirection().normalize();

        Vector descentVelocity = direction.clone().multiply(descentHorizontalStrength);
        descentVelocity.setY(-descentVerticalStrength);
        hero.getController().setVelocity(descentVelocity);

        descentSound.play(loc, hero.getController());
        descentVisual.play(loc, new Vector(0, -1, 0));
    }

    @Override
    protected void onCompositeTick(Hero hero) {
        if (!isActive) return;

        if (waitingForLaunch) {
            launchDelayCounter--;
            if (launchDelayCounter <= 0) {
                waitingForLaunch = false;
                beginLaunch(hero);
            }
        }

        if (waitingForDescent) {
            descentDelayCounter--;
            if (descentDelayCounter <= 0) {
                waitingForDescent = false;
                beginDescent(hero);
            }
        }

        if (isDescending) {
            checkDescentHitbox(hero);
        }
    }

    private void checkDescentHitbox(Hero hero) {
        LivingEntity entity = hero.getController().getEntity();
        Location center = hero.getController().getCenter();

        Hitbox.sphere(center, radius)
                .exclude(entity)
                .excludeTeammates(entity)
                .filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))
                .shareHitHistory(descentHitHistory)
                .onHit((victim, loc) -> onDescentHit(hero, victim))
                .check();
    }

    private void onDescentHit(Hero hero, LivingEntity victim) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hitVisual.play(victim.getEyeLocation());

        Vector downward = new Vector(0, -1, 0);
        hit(hero, victim).damage(damage).knockback(knockback).direction(downward).execute();
    }

    @Override
    public void onLand(Hero hero) {
        if (!isActive || !isDescending) return;

        isDescending = false;
        landingShockwave.perform(hero, new HashMap<>(descentHitHistory));

        if (smashesRemaining > 0) {
            performSmash(hero);
        } else {
            endUltimate(hero);
        }
    }

    private void endUltimate(Hero hero) {
        if (!isActive) return;

        isActive = false;
        waitingForLaunch = false;
        launchDelayCounter = 0;
        isDescending = false;
        waitingForDescent = false;
        descentDelayCounter = 0;
        smashesRemaining = 0;
        descentHitHistory.clear();

        if (hero.getSecondaryAbility() != null) {
            hero.getSecondaryAbility().getCooldownObject().removeDurationModifier(this);
        }
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public String getActiveStatusText() {
        if (!isActive) return null;
        return (smashesRemaining + 1) + (smashesRemaining == 0 ? " smash left" : " smashes left");
    }
}
