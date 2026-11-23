package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import me.stolyy.heroes.util.Configs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public abstract class Dash extends BaseAbility {
    protected double speed;
    protected int ticks;
    protected int trailSpacing;
    protected int hitboxGraceTicks;
    protected double exitVelocityFactor;
    
    protected SoundEffect activationSound, hitSound;
    protected VisualEffect dashVisual, trailVisual;

    protected DashInstance currentDash = null;

    public Dash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speed = this.config.getDouble("speed", 3.0);
        this.ticks = this.config.getInt("duration", 7);

        this.trailSpacing = this.config.getInt("visuals.trail_spacing", 2);
        this.hitboxGraceTicks = this.config.getInt("hitbox_grace_ticks", 5);
        this.exitVelocityFactor = Configs.orTemplate(this.config, "exit_velocity_factor", "dash.exit_velocity_factor");
        
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        
        this.dashVisual = new VisualEffect(this.config.getConfigurationSection("visuals.dash_visual"));
        this.trailVisual = new VisualEffect(this.config.getConfigurationSection("visuals.trail_visual"));
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

        Vector direction = hero.getController().getLocation().getDirection().normalize();
        currentDash = new DashInstance(hero, direction);
    }

    @Override
    protected void onTick(Hero hero) {
        if (currentDash != null) {
            if (currentDash.tick()) {
                currentDash = null;
            }
        }
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
        protected final Hitbox hitbox;
        private boolean inGracePhase = false;
        private int graceTicksRemaining = 0;

        public DashInstance(Hero hero, Vector direction) {
            this.hero = hero;
            this.direction = direction;
            this.velocity = direction.clone().multiply(dashSpeed());
            this.previousCenter = hero.getController().getCenter();
            // Length is set per tick from the distance actually travelled; this is just the seed.
            this.hitbox = Hitbox.box(previousCenter, direction, radius * 2, radius * 2, radius)
                    .exclude(hero.getController().getEntity())
                    .excludeTeammates(hero.getController().getEntity())
                    .trackHits()
                    .onHit((victim, loc) -> onHit(hero, victim, direction));

            // Initial visual if needed
            dashVisual.play(hero.getController().getCenter(), direction, hero.getSkin().getId());
        }

        public boolean tick() {
            Location center = hero.getController().getCenter();

            if (inGracePhase) {
                checkCollisions(center);
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
                    hero.getController().getEntity().setVelocity(velocity.clone().multiply(exitVelocityFactor));
                    return false;
                }
                hero.getController().getEntity().setVelocity(velocity.clone().multiply(exitVelocityFactor));
                cleanup();
                return true;
            }

            LivingEntity entity = hero.getController().getEntity();
            entity.setVelocity(velocity);

            // setVelocity does not move the entity synchronously — the step it schedules lands
            // before the next ability tick, which is exactly the segment the next sweep covers.
            checkCollisions(center);
            updateVisuals(center, entity.getLocation());

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

            hitbox.moveTo(previousCenter).reorient(sweepDirection).resize(travelled + radius).check();
            previousCenter = center.clone();
        }

        protected void updateVisuals(Location center, Location footLoc) {
            trailTickCounter++;
            if (trailTickCounter >= trailSpacing) {
                trailTickCounter = 0;
                trailVisual.play(center, direction, hero.getSkin().getId());
            }
        }

        public void cleanup() { }
    }

    protected double calcDamage(Hero hero, LivingEntity victim) { return damage; }
    protected double calcKnockback(Hero hero, LivingEntity victim) { return knockback; }

    protected void applyInteraction(Hero hero, LivingEntity victim, Vector dashDir) {
        double dmg = calcDamage(hero, victim);
        double kb = calcKnockback(hero, victim);
        Interactions.on(hero.getController().getEntity(), victim).abilityName(getName()).damage(dmg).knockback(kb).direction(dashDir).execute();
    }

    protected void onHit(Hero hero, LivingEntity victim, Vector dashDir) {
        hitSound.play(hero.getController().getLocation(), hero.getController());
        applyInteraction(hero, victim, dashDir);
        hero.triggerAbilityHit(this, victim);
    }

    /**
     * Launch speed for the dash about to start. Read ONCE, in the DashInstance constructor, so an
     * in-flight dash keeps the speed it launched with even if the source of a bonus goes away
     * mid-lunge. Override for per-cast speed changes (e.g. Sgt's parry-empowered bash); use
     * {@link #setSpeed(double)} for persistent ones (e.g. Bug's Sharp Shadow charm).
     */
    protected double dashSpeed() { return speed; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public int getDurationTicks() { return ticks; }
    public void setDurationTicks(int ticks) { this.ticks = ticks; }

    @Override public void onHit(Hero attacker, LivingEntity victim) { }
}
