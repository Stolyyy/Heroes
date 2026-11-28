package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
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
    private final SoundEffect activationSound;
    private final SoundEffect activeSound;
    private final SoundEffect hitSound;
    private final VisualEffect trailVisual;

    private boolean revActive = false;
    private Hitbox hitbox;
    private int ticksSinceLoopSound = 0;

    public ChainRev(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.hitIntervalMs = Configs.requireInt(this.config, "hit_interval") * 50L;
        this.speed = Configs.requireDouble(this.config, "speed");
        this.groundedSpeed = Configs.requireDouble(this.config, "grounded_speed");
        this.energyCostPerTick = Configs.requireDouble(this.config, "energy_cost_per_tick");
        this.energyCostPerTickHitting = Configs.requireDouble(this.config, "energy_cost_per_tick_hitting");
        this.hitDiscountWindowMs = (long) (Configs.requireDouble(this.config, "hit_discount_window") * 1000);
        this.groundedKnockback = Configs.requireDouble(this.config, "grounded_knockback");
        this.groundedKnockbackY = Configs.requireDouble(this.config, "grounded_knockback_y");
        this.airbornePitch = Configs.requireDouble(this.config, "airborne_pitch");
        this.damageWindowMs = Configs.requireLong(this.config, "damage_window_ms");
        this.energyDrainMult = Configs.requireDouble(this.config, "energy_drain_mult");
        this.damageTakenMult = Configs.requireDouble(this.config, "damage_taken_mult");
        this.loopSoundInterval = Configs.requireInt(this.config, "loop_sound_interval");
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
        this.activeSound = new SoundEffect(Configs.requireSection(this.config, "visuals.active_sound"));
        this.hitSound = new SoundEffect(Configs.requireSection(this.config, "visuals.hit_sound"));
        this.trailVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.trail_visual"));
    }

    @Override
    protected boolean canUse(Hero hero) {
        return !revActive;
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
                .filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))
                .trackHits(hitIntervalMs)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    lastHitMs = System.currentTimeMillis();
                    Interactions.InteractionBuilder interaction = hit(hero, victim)
                            .damage(damage)
                            .staticKnockback();
                    if (hero.getController().isOnGround()) {
                        interaction.knockback(groundedKnockback)
                                .verticalKnockback(groundedKnockbackY)
                                .direction(travelDirection(hero))
                                .additive(true);
                    } else {
                        interaction.knockback(knockback);
                    }
                    interaction.execute();
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
        hero.getAttributes().addDamageTakenMultiplier(this, damageTakenMult);
    }

    private void closeDamageWindow(Hero hero) {
        if (!damageWindowOpen) return;
        damageWindowOpen = false;
        hero.getAttributes().removeDamageTakenModifier(this);
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

        if (!hero.getController().isUseHeld()) {
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

        hero.getController().setVelocity(moveDirection(hero));
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
            if (hero.getController() != null) {
                hero.getController().setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        closeDamageWindow(hero);
        revActive = false;
    }
}
