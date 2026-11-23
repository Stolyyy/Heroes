package me.stolyy.heroes.ability.kit.bulk;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.Shockwave;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BulkSmash extends BaseAbility {
    private final int smashCount;

    private final int launchDelayTicks;
    private final double launchHorizontalStrength;
    private final double launchVerticalStrength;
    private final int descentDelayTicks;
    private final double descentHorizontalStrength;
    private final double descentVerticalStrength;

    private final double secondaryCooldownMultiplier;

    private final LandingShockwave landingShockwave;

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
    private final Set<UUID> descentHitHistory = new HashSet<>();
    private long originalSecondaryCooldown;

    public BulkSmash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.smashCount = config.getInt("smash_count");
        this.launchDelayTicks = config.getInt("launch_delay_ticks");
        this.launchHorizontalStrength = config.getDouble("launch_horizontal_strength");
        this.launchVerticalStrength = config.getDouble("launch_vertical_strength");
        this.descentDelayTicks = config.getInt("descent_delay_ticks");
        this.descentHorizontalStrength = config.getDouble("descent_horizontal_strength");
        this.descentVerticalStrength = config.getDouble("descent_vertical_strength");

        this.secondaryCooldownMultiplier = config.getDouble("secondary_cooldown_multiplier");

        this.landingShockwave = new LandingShockwave(config.getConfigurationSection("shockwave"), skin);

        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.launchSound = new SoundEffect(this.config.getConfigurationSection("visuals.launch_sound"));
        this.descentSound = new SoundEffect(this.config.getConfigurationSection("visuals.descent_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.launchVisual = new VisualEffect(this.config.getConfigurationSection("visuals.launch_visual"));
        this.descentVisual = new VisualEffect(this.config.getConfigurationSection("visuals.descent_visual"));
        this.hitVisual = new VisualEffect(this.config.getConfigurationSection("visuals.hit_visual"));
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        endUltimate(hero);
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;
        smashesRemaining = smashCount;
        descentHitHistory.clear();


        originalSecondaryCooldown = hero.getSecondaryAbility().getCooldown();
        long modifiedCooldown = (long) (originalSecondaryCooldown * secondaryCooldownMultiplier);
        hero.getSecondaryAbility().setCooldown(modifiedCooldown);

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
        LivingEntity entity = hero.getController().getEntity();
        Vector direction = entity.getLocation().getDirection().normalize();

        Vector launchVelocity = direction.clone().multiply(launchHorizontalStrength);
        launchVelocity.setY(launchVerticalStrength);
        entity.setVelocity(launchVelocity);

        launchSound.play(entity.getLocation(), hero.getController());
        launchVisual.play(entity.getLocation(), direction);

        waitingForDescent = true;
        descentDelayCounter = descentDelayTicks;
    }

    private void beginDescent(Hero hero) {
        isDescending = true;

        LivingEntity entity = hero.getController().getEntity();
        Vector direction = entity.getLocation().getDirection().normalize();

        Vector descentVelocity = direction.clone().multiply(descentHorizontalStrength);
        descentVelocity.setY(-descentVerticalStrength);
        entity.setVelocity(descentVelocity);

        descentSound.play(entity.getLocation(), hero.getController());
        descentVisual.play(entity.getLocation(), new Vector(0, -1, 0));
    }

    @Override
    protected void onTick(Hero hero) {
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
        Location center = entity.getBoundingBox().getCenter().toLocation(entity.getWorld());

        Hitbox hitbox = Hitbox.sphere(center, radius)
                .exclude(entity)
                .filter(e -> !descentHitHistory.contains(e.getUniqueId()))
                .onHit((victim, loc) -> {
                    descentHitHistory.add(victim.getUniqueId());
                    onDescentHit(hero, victim);
                });

        hitbox.excludeTeammates(entity);

        hitbox.check();
    }

    private void onDescentHit(Hero hero, LivingEntity victim) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hitVisual.play(victim.getEyeLocation());

        Vector downward = new Vector(0, -1, 0);
        Interactions.on(hero.getController().getEntity(), victim).abilityName(getName()).damage(damage).knockback(knockback).direction(downward).execute();

        hero.triggerAbilityHit(this, victim);
    }

    @Override
    public void onLand(Hero hero) {
        if (!isActive || !isDescending) return;

        isDescending = false;
        performShockwave(hero);

        if (smashesRemaining > 0) {
            performSmash(hero);
        } else {
            endUltimate(hero);
        }
    }

    private void performShockwave(Hero hero) {
        landingShockwave.setExcludedTargets(descentHitHistory);
        landingShockwave.execute(hero);
    }

    private static class LandingShockwave extends Shockwave {
        private Set<UUID> excludedTargets = new HashSet<>();

        public LandingShockwave(ConfigurationSection config, HeroSkin skin) {
            super(config, skin);
        }

        public void setExcludedTargets(Set<UUID> targets) {
            this.excludedTargets = new HashSet<>(targets);
        }

        @Override
        protected void onHit(Hero hero, LivingEntity victim, Location center) {
            if (excludedTargets.contains(victim.getUniqueId())) return;
            super.onHit(hero, victim, center);
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

        hero.getSecondaryAbility().setCooldown(originalSecondaryCooldown);
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
