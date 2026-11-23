package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.listener.AbilityListener;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class ChainRev extends BaseAbility {
    private final long hitIntervalMs;

    private final double speed;
    private final double groundedSpeed;
    private final double energyCostPerTick;
    private final double energyCostPerTickHitting;
    private final long hitDiscountWindowMs;

    private final double groundedKnockback;
    private final double groundedKnockbackY;

    private final double airbornePitch;
    private final int loopSoundInterval;
    private long lastHitMs;

    private final long damageWindowMs;
    private final double energyDrainMult;
    private final double damageTakenMult;
    private long lastDamageDealtMs;
    private boolean damageWindowOpen;
    private double baseDamageTakenMult;
    private double appliedDamageTakenMult;
    private final SoundEffect activationSound;
    private final SoundEffect activeSound;
    private final SoundEffect hitSound;
    private final VisualEffect trailVisual;

    private boolean revActive = false;
    private Hitbox hitbox;
    private int ticksSinceLoopSound = 0;

    public ChainRev(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.hitIntervalMs = me.stolyy.heroes.util.Configs.requireInt(this.config, "hit_interval") * 50L;
        this.speed = me.stolyy.heroes.util.Configs.requireDouble(this.config, "speed");
        this.groundedSpeed = me.stolyy.heroes.util.Configs.requireDouble(this.config, "grounded_speed");
        this.energyCostPerTick = me.stolyy.heroes.util.Configs.requireDouble(this.config, "energy_cost_per_tick");
        this.energyCostPerTickHitting = me.stolyy.heroes.util.Configs.requireDouble(this.config, "energy_cost_per_tick_hitting");
        this.hitDiscountWindowMs = (long) (me.stolyy.heroes.util.Configs.requireDouble(this.config, "hit_discount_window") * 1000);
        this.groundedKnockback = me.stolyy.heroes.util.Configs.requireDouble(this.config, "grounded_knockback");
        this.groundedKnockbackY = me.stolyy.heroes.util.Configs.requireDouble(this.config, "grounded_knockback_y");
        this.airbornePitch = me.stolyy.heroes.util.Configs.requireDouble(this.config, "airborne_pitch");
        this.damageWindowMs = me.stolyy.heroes.util.Configs.requireLong(this.config, "damage_window_ms");
        this.energyDrainMult = me.stolyy.heroes.util.Configs.requireDouble(this.config, "energy_drain_mult");
        this.damageTakenMult = me.stolyy.heroes.util.Configs.requireDouble(this.config, "damage_taken_mult");
        this.loopSoundInterval = this.config.getInt("loop_sound_interval", 8);
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.activeSound = new SoundEffect(this.config.getConfigurationSection("visuals.active_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.trailVisual = new VisualEffect(this.config.getConfigurationSection("visuals.trail_visual"));
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (revActive) return false;
        return super.canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        revActive = true;
        ticksSinceLoopSound = 0;
        activationSound.play(hero.getController().getLocation(), hero.getController());

        LivingEntity self = hero.getController().getEntity();
        hitbox = Hitbox.sphere(hero.getController().getCenter(), radius)
                .exclude(self)
                .excludeTeammates(self)
                .trackHits(hitIntervalMs)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    onHit(hero, victim);
                    lastHitMs = System.currentTimeMillis();
                    if (hero.getController().isOnGround()) {
                        Interactions.on(self, victim)
                                .abilityName(getName())
                                .damage(damage)
                                .knockback(groundedKnockback)
                                .verticalKnockback(groundedKnockbackY)
                                .direction(travelDirection(hero))
                                .staticKnockback()
                                .additive(true)
                                .execute();
                    } else {
                        Interactions.on(self, victim)
                                .abilityName(getName())
                                .damage(damage)
                                .knockback(knockback)
                                .staticKnockback()
                                .execute();
                    }
                });
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        if (hero.getCombat().getDamage() > 0) openDamageWindow(hero);
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (ability.getDamage() > 0) openDamageWindow(hero);
    }

    private void openDamageWindow(Hero hero) {
        lastDamageDealtMs = System.currentTimeMillis();
        if (damageWindowOpen) return;
        damageWindowOpen = true;
        baseDamageTakenMult = hero.getAttributes().getDamageMultiplier();
        appliedDamageTakenMult = baseDamageTakenMult * damageTakenMult;
        hero.getAttributes().setDamageMultiplier(appliedDamageTakenMult);
    }

    // Restores only if nothing else (Hero Of Hell) has rewritten the multiplier since we applied ours.
    private void closeDamageWindow(Hero hero) {
        if (!damageWindowOpen) return;
        damageWindowOpen = false;
        if (hero.getAttributes().getDamageMultiplier() == appliedDamageTakenMult) {
            hero.getAttributes().setDamageMultiplier(baseDamageTakenMult);
        }
    }

    @Override
    protected void onTick(Hero hero) {
        if (damageWindowOpen && System.currentTimeMillis() - lastDamageDealtMs > damageWindowMs) {
            closeDamageWindow(hero);
        }
        if (!revActive) return;

        if (!cooldown.isInUse()) {
            revActive = false;
            return;
        }

        if (hero.getController().isPlayer()
                && !AbilityListener.isUseHeld(hero.getController().getUniqueId())) {
            cancelRev(hero);
            return;
        }

        float drain = (float) ((System.currentTimeMillis() - lastHitMs) <= hitDiscountWindowMs
                ? energyCostPerTickHitting : energyCostPerTick);
        if (damageWindowOpen) drain *= (float) energyDrainMult;
        if (!hero.getEnergy().tryConsume(hero.getController(), drain)) {
            cancelRev(hero);
            return;
        }

        hero.getController().getEntity().setVelocity(moveDirection(hero));
        hitbox.moveTo(hero.getController().getCenter()).check();

        ticksSinceLoopSound++;
        if (ticksSinceLoopSound >= loopSoundInterval) {
            ticksSinceLoopSound = 0;
            activeSound.play(hero.getController().getLocation(), hero.getController());
        }
        trailVisual.play(hero.getController().getCenter());
    }

    private Vector moveDirection(Hero hero) {
        Location loc = hero.getController().getLocation();
        double yaw = Math.toRadians(loc.getYaw());
        if (hero.getController().isOnGround() && loc.getPitch() > -airbornePitch) {
            return new Vector(-Math.sin(yaw), 0, Math.cos(yaw)).multiply(groundedSpeed);
        }
        double pitch = Math.toRadians(loc.getPitch());
        double xz = Math.cos(pitch);
        return new Vector(-Math.sin(yaw) * xz, -Math.sin(pitch), Math.cos(yaw) * xz).multiply(speed);
    }

    private Vector travelDirection(Hero hero) {
        double yaw = Math.toRadians(hero.getController().getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    public boolean isActive() {
        return revActive;
    }

    public void cancelRev(Hero hero) {
        if (!revActive) return;
        revActive = false;
        if (cooldown.isInUse()) cooldown.reset();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        closeDamageWindow(hero);
        if (revActive) {
            revActive = false;
            if (hero.getController() != null && hero.getController().getEntity() != null) {
                hero.getController().getEntity().setVelocity(new Vector(0, 0, 0));
            }
        }
    }
}
