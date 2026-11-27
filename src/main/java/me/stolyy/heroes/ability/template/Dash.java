package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.ModifierStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * Linear dash template driving wall-clamped per-tick movement, trail emission, and swept
 * collision checks.
 */
public abstract class Dash extends BaseAbility {
    private double baseSpeed;
    protected double speed;
    private final ModifierStack speedMods = new ModifierStack();
    protected int ticks;
    protected int trailSpacing;
    protected int hitboxGraceTicks;
    protected double exitVelocityFactor;
    protected double verticalKnockback;
    protected boolean kbReverse;
    protected Double kbPitchDeg;

    protected SoundEffect activationSound, hitSound;
    protected VisualEffect dashVisual, trailVisual;

    protected DashInstance currentDash = null;

    public Dash(ConfigurationSection config, HeroSkin skin) {
        this(config, skin, null);
    }

    protected Dash(ConfigurationSection config, HeroSkin skin, AbilityType explicitType) {
        super(config, skin, explicitType);
        this.baseSpeed = Configs.orTemplate(this.config, "speed", "dash.speed");
        this.speed = this.baseSpeed;
        this.ticks = Configs.orTemplateInt(this.config, "duration", "dash.duration");

        this.trailSpacing = Configs.orTemplateInt(this.config, "visuals.trail_spacing", "dash.trail_spacing");
        this.hitboxGraceTicks = Configs.orTemplateInt(this.config, "hitbox_grace_ticks", "dash.hitbox_grace_ticks");
        this.exitVelocityFactor = Configs.orTemplate(this.config, "exit_velocity_factor", "dash.exit_velocity_factor");
        this.verticalKnockback = Configs.orTemplate(this.config, "vertical_knockback", "dash.vertical_knockback");
        this.kbReverse = Configs.orTemplateBoolean(this.config, "kb_reverse", "dash.kb_reverse");
        this.kbPitchDeg = this.config.contains("kb_pitch_deg") ? Configs.requireDouble(this.config, "kb_pitch_deg") : null;

        this.activationSound = SoundEffect.optional(this.config, "visuals.activation_sound");
        this.hitSound = SoundEffect.optional(this.config, "visuals.hit_sound");

        this.dashVisual = VisualEffect.optional(this.config, "visuals.dash_visual");
        this.trailVisual = VisualEffect.optional(this.config, "visuals.trail_visual");
    }

    @Override
    protected boolean canUse(Hero hero) {
        return currentDash == null;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        if (currentDash != null) {
            currentDash.cleanup();
            currentDash = null;
        }
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());
        currentDash = new DashInstance(hero, dashDirection(hero));
    }

    protected Vector dashDirection(Hero hero) {
        return hero.getController().getLocation().getDirection().normalize();
    }

    protected Map<UUID, Long> sharedHitHistory() { return null; }
    protected boolean shouldEndDash(Hero hero) { return false; }
    protected void onDashEnd(Hero hero) {}
    protected void onDashTick(Hero hero) {}

    @Override
    protected final void onTick(Hero hero) {
        if (currentDash != null) {
            if (shouldEndDash(hero) || currentDash.tick()) {
                currentDash.cleanup();
                currentDash = null;
                onDashEnd(hero);
            }
        }
        onDashTick(hero);
    }

    protected class DashInstance {
        protected final Hero hero;
        protected final Vector direction;
        protected final Vector velocity;
        protected int elapsed = 0;
        protected int trailTickCounter = 0;
        // Swept collision. Bukkit velocity is BLOCKS PER TICK, so a dash covers several blocks
        // between consecutive ability ticks — far more than any sane hitbox is wide. A hitbox
        // sampled once per tick at the dasher's current position therefore leaves unchecked gaps
        // between samples, and anyone standing in a gap is passed straight through. Each tick
        // instead sweeps a box along the segment ACTUALLY travelled since the last sample, so the
        // path is covered continuously at any speed.
        protected Location previousCenter;
        protected Vector lastSweepDirection;
        protected final Hitbox hitbox;
        private boolean inGracePhase = false;
        private int graceTicksRemaining = 0;

        public DashInstance(Hero hero, Vector direction) {
            this.hero = hero;
            this.direction = direction;
            this.lastSweepDirection = direction;
            this.velocity = direction.clone().multiply(dashSpeed());
            this.previousCenter = hero.getController().getCenter();
            // Length is set per tick from the distance actually travelled; this is just the seed.
            this.hitbox = Hitbox.box(previousCenter, direction, radius * 2, radius * 2, radius)
                    .exclude(hero.getController().getEntity())
                    .excludeTeammates(hero.getController().getEntity())
                    .filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))
                    .onHit((victim, loc) -> onHit(hero, victim, lastSweepDirection));

            Map<UUID, Long> shared = sharedHitHistory();
            if (shared != null) {
                this.hitbox.shareHitHistory(shared);
            } else {
                this.hitbox.trackHits();
            }

            // Initial visual if needed
            dashVisual.play(hero.getController().getCenter(), direction, hero.getSkin().getId());

            if (ticks <= 0) {
                hero.getController().setVelocity(velocity.clone().multiply(exitVelocityFactor));
                if (hitboxGraceTicks > 0) {
                    this.inGracePhase = true;
                    this.graceTicksRemaining = hitboxGraceTicks;
                }
            }
        }

        public boolean tick() {
            Location center = hero.getController().getCenter();
            if (center == null) {
                cleanup();
                return true;
            }

            if (inGracePhase) {
                checkCollisions(center);
                if (exitVelocityFactor > 0) {
                    updateVisuals(center);
                }
                graceTicksRemaining--;
                if (graceTicksRemaining <= 0) {
                    cleanup();
                    return true;
                }
                return false;
            }

            if (elapsed >= ticks) {
                if (hitboxGraceTicks > 0) {
                    checkCollisions(center);
                    inGracePhase = true;
                    graceTicksRemaining = hitboxGraceTicks;
                    hero.getController().setVelocity(velocity.clone().multiply(exitVelocityFactor));
                    return false;
                }
                hero.getController().setVelocity(velocity.clone().multiply(exitVelocityFactor));
                cleanup();
                return true;
            }

            hero.getController().setVelocity(velocity);

            // setVelocity does not move the entity synchronously — the step it schedules lands
            // before the next ability tick, which is exactly the segment the next sweep covers.
            checkCollisions(center);
            updateVisuals(center);

            elapsed++;
            return false;
        }

        /**
         * Sweeps a box along the path travelled since the previous check and hits everyone it
         * crosses (each victim at most once per dash, via the inherited hit history). The box is
         * {@code radius} half-extents thick, so {@code radius} is the reach out from the dash line.
         */
        protected void checkCollisions(Location center) {
            Vector delta = center.toVector().subtract(previousCenter.toVector());
            double travelled = delta.length();
            // A stalled tick (dash start, wall stop, grace phase) has no segment to sweep, and
            // BoundingBox#rayTrace throws on a zero-magnitude direction — fall back to the dash's
            // own aim. Unlike a projectile the dash keeps a `+ radius` reach past its endpoint:
            // the grace phase is a deliberate lingering hitbox, and a zero-length ray only ever
            // reports targets already overlapping the origin.
            Vector sweepDirection = travelled > 1.0E-6 ? delta.multiply(1 / travelled) : direction;
            lastSweepDirection = sweepDirection;

            hitbox.moveTo(previousCenter).reorient(sweepDirection).resize(travelled + radius).check();
            previousCenter = center.clone();
        }

        protected void updateVisuals(Location center) {
            trailTickCounter++;
            if (trailTickCounter >= trailSpacing) {
                trailTickCounter = 0;
                trailVisual.play(center, direction, hero.getSkin().getId());
            }
        }

        public void cleanup() { }
    }

    protected void applyInteraction(Hero hero, LivingEntity victim, Vector dashDir) {
        Interactions.InteractionBuilder interaction = hit(hero, victim);
        double dmg = calcDamage(hero, victim);
        double kb = calcKnockback(hero, victim);
        Vector dir = kbReverse ? dashDir.clone().multiply(-1) : dashDir;
        interaction.damage(dmg)
                .knockback(kb)
                .verticalKnockback(verticalKnockback)
                .direction(dir);
        if (kbPitchDeg != null) interaction.pitch(kbPitchDeg);
        interaction.execute();
    }

    protected void onHit(Hero hero, LivingEntity victim, Vector dashDir) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        applyInteraction(hero, victim, dashDir);
    }

    /**
     * Launch speed for the dash about to start. Read ONCE, in the DashInstance constructor, so an
     * in-flight dash keeps the speed it launched with even if the source of a bonus goes away
     * mid-lunge. Override for per-cast speed changes (e.g. Sgt's parry-empowered bash); use
     * {@link #addSpeedMultiplier(Object, double)} for persistent ones (e.g. Bug's Sharp Shadow charm).
     */
    protected double dashSpeed() { return speed; }

    public double getSpeed() { return speed; }

    protected void setBaseSpeed(double baseSpeed) {
        this.baseSpeed = baseSpeed;
        recomputeSpeed();
    }

    public void addSpeedMultiplier(Object key, double mult) { speedMods.putMult(key, mult); recomputeSpeed(); }
    public void addSpeedFlat(Object key, double delta) { speedMods.putFlat(key, delta); recomputeSpeed(); }
    public void removeSpeedModifier(Object key) { speedMods.remove(key); recomputeSpeed(); }
    private void recomputeSpeed() { this.speed = speedMods.evaluate(baseSpeed, 0.0); }

    public int getDurationTicks() { return ticks; }
}
