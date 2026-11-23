package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class Projectile extends RangedAbility {
    protected double speed;
    protected double gravity;

    /**
     * Hard ceiling on one instance's lifetime, in ticks. ceil(range/speed) is the shortest a flight
     * can possibly be — {@link ProjectileInstance#move()} credits at most one tick of muzzle speed
     * against the range budget — so this is six times the ideal flight plus two seconds. That sits
     * comfortably above the slowest legitimate flight: a straight-up lob loiters an extra
     * ~speed/gravity ticks around its apex before it is back up to muzzle speed, ~25 for the
     * skullfire grenade against an ideal 38. Nothing the range test already expires can reach it;
     * it exists for the subclasses that replace tick() with conditional exits (boomerang, saber,
     * ricochet) and for degenerate launches such as Defecake's straight-down (0,-1,0) throw. The
     * cast saturates, so a config with speed 0 yields Integer.MAX_VALUE — effectively no cap —
     * rather than an overflowed negative one that would kill every shot on its first tick.
     */
    protected final int maxLifetimeTicks;

    protected VisualEffect projVisual;

    protected final List<ProjectileInstance> activeProjectiles = new ArrayList<>();

    public Projectile(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speed = this.config.getDouble("speed", 1.0);
        this.gravity = this.config.getDouble("gravity", 0.0);
        this.maxLifetimeTicks = (int) (Math.ceil(range / speed) * 6 + 40);
        this.projVisual = new VisualEffect(this.config.getConfigurationSection("visuals.projectile_visual"));

        // Projectile's default trail_spacing is 2 ticks (vs RangedAbility's 1); override if not set.
        if (!this.config.contains("visuals.trail_spacing")) {
            this.trailSpacing = 2;
        }
    }

    @Override
    protected void fireSingleShot(Hero hero) {
        fireSound.play(hero.getController().getLocation(), hero.getController());
        activeProjectiles.add(new ProjectileInstance(hero, getStartLocation(hero)));
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);
        // Iterate a snapshot: a detonation that scores the winning kill tears the game down
        // synchronously inside tick(), which reaches onOwnerLost -> activeProjectiles.clear().
        // A removeIf over the live list would blow up with ConcurrentModificationException.
        for (ProjectileInstance p : new ArrayList<>(activeProjectiles)) {
            if (p.tick()) activeProjectiles.remove(p);
        }
    }

    /** In-flight projectiles are detached world state — they keep ticking while the owner is dormant. */
    @Override
    public boolean isIndependent() { return true; }

    /** Owner reference lost (logout/game end): expire all in-flight instances and their displays. */
    @Override
    public void onOwnerLost(Hero hero) {
        for (ProjectileInstance p : new ArrayList<>(activeProjectiles)) {
            p.cleanup();
        }
        activeProjectiles.clear();
    }

    protected class ProjectileInstance {
        protected final Hero hero;
        protected final Location currentLoc;
        protected final Vector velocity;
        protected double distanceTraveled = 0;
        protected int trailTickCounter = 0;
        /** Distance traveled since the last trail emission. Used when {@code trailSpacingDistance > 0}. */
        protected double distanceTrailAccumulator = 0;
        protected final Hitbox hitbox;
        protected boolean expired = false;
        /** Ticks this instance has been alive, bounded by {@link Projectile#maxLifetimeTicks}. */
        protected int ticksAlive = 0;
        /**
         * Where the flight actually ended, when the segment just swept resolved a contact: the
         * victim contact point, else the wall point. Null on a clean expiry. Cleared at the top of
         * every {@link #checkCollisions} so a piercing shot cannot carry a stale one to its expiry.
         */
        protected Location impactLocation;
        protected ItemDisplay displayEntity;
        /** Persistent trail displays (only populated when {@code trailPersists} is true). Removed on cleanup. */
        protected final List<ItemDisplay> trailDisplays = new ArrayList<>();

        public ProjectileInstance(Hero hero, Location start) {
            this.hero = hero;
            this.currentLoc = start.clone();
            this.velocity = getDirection(start).clone().multiply(speed);
            this.hitbox = Hitbox.cylinder(start, velocity, radius, velocity.length())
                    .exclude(hero.getController().getEntity())
                    .excludeTeammates(hero.getController().getEntity())
                    .checkWalls(!piercesWalls)
                    .pierce(piercesPlayers)
                    .trackHits()
                    .onHit((victim, loc) -> {
                        lastHitDistance = distanceTraveled;
                        impactLocation = loc;
                        onHit(hero, victim, loc);
                    })
                    .onHitWall(loc -> onHitWall(hero, loc));
        }

        public boolean tick() {
            if (expired) return true;
            if (overLifetime()) return true;
            Location prevLoc = currentLoc.clone();
            move();

            // Collisions resolve BEFORE the range test. With the two the other way round the final
            // segment was never swept: the instance integrated past its budget, expired and tore
            // itself down without ever asking what it had just flown through. That left a band
            // roughly one step deep at maximum range where a shot passes through players and
            // terrain and then detonates in mid-air on the far side of them.
            if (checkCollisions(prevLoc)) {
                finish(prevLoc);
                return true;
            }

            if (distanceTraveled > range) {
                onExpire(hero, currentLoc);
                finish(prevLoc);
                return true;
            }

            displayVisuals(prevLoc);
            return false;
        }

        /**
         * Hard lifetime backstop. The range budget is the only expiry the base tick has, and the
         * subclasses that replace tick() outright trade it for conditional rules that are not
         * guaranteed to fire — a ricochet that keeps acquiring fresh targets never starts its
         * return leg. Terminates silently, exactly like those subclasses' own distance guards do:
         * no onExpire, so nothing detonates on a technicality. Unreachable in normal play, see
         * {@link Projectile#maxLifetimeTicks}.
         *
         * <p>Also the practice-range fence: the lobby arena is open-topped and wall-less in places,
         * so a practicing owner's shot that leaves the arena box expires here, through the same
         * silent teardown — every subclass that replaces tick() already routes through this.
         */
        protected boolean overLifetime() {
            if (++ticksAlive <= maxLifetimeTicks && !leftPracticeArena()) return false;
            cleanup();
            return true;
        }

        private boolean leftPracticeArena() {
            return hero.getController().getEntity() instanceof Player owner
                    && PracticeManager.isPracticing(owner)
                    && !PracticeManager.arenaOf(owner).contains(currentLoc);
        }

        /**
         * Last visible frame, then teardown. cleanup() on its own removes the display while the
         * client is still interpolating toward the PREVIOUS tick's position (teleportDuration is 1),
         * so the model vanishes up to two steps short of where the flight actually ended — several
         * blocks for a fast grenade. Put it on the resolved end point first and let the removal land
         * a tick later: a teleport and a remove in the same tick never reach the client as a
         * position at all, so removing immediately after the teleport would draw nothing.
         */
        protected void finish(Location prevLoc) {
            Location end = impactLocation != null ? impactLocation : currentLoc;
            if (displayEntity != null && !displayEntity.isDead()) {
                Vector heading = end.toVector().subtract(prevLoc.toVector());
                displayEntity.teleport(AbstractDisplayBuilder.canonical(end));
                if (heading.lengthSquared() > 1.0E-12) {
                    AbstractDisplayBuilder.orient(displayEntity, heading, projVisual.modelBackOffset());
                }
                ItemDisplay lastFrame = displayEntity;
                displayEntity = null; // cleanup() must not undo the frame just drawn
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), lastFrame::remove, 2L);
            }
            cleanup();
        }

        protected void move() {
            // Velocity-Verlet: half a tick of gravity before the step, half after. Plain forward
            // Euler (step first, then subtract the whole tick of gravity) gave the first step ZERO
            // gravity and integrated every later step at its start-of-tick velocity, so the whole
            // arc rode systematically ABOVE the true parabola — over a block high by expiry for the
            // slower lobs, and reading in flight as gravity kicking in a tick late. Half-kicking is
            // the same cost and lands on the exact parabola. Gravity-free projectiles are untouched:
            // gravity/2 is 0.
            velocity.setY(velocity.getY() - gravity / 2);
            currentLoc.add(velocity);
            // Credit at most one tick of muzzle speed. distanceTraveled IS the range budget, and the
            // raw step is the 3-D diagonal AFTER gravity has been stacking onto Y for the whole
            // flight, so a lobbed shot burns its budget faster the steeper it is aimed: `range`
            // stopped meaning blocks downrange and both reach and lifetime swung with aim pitch.
            // Clamping to speed removes that dependence without moving the nominal numbers for a
            // flat, gravity-free shot, which is every projectile that does not set `gravity`.
            distanceTraveled += Math.min(velocity.length(), speed);
            velocity.setY(velocity.getY() - gravity / 2);
        }

        protected boolean checkCollisions(Location prevLoc) {
            // Sweep the segment ACTUALLY travelled this tick. Sizing the sweep from the spawn
            // velocity (as this used to) goes stale the moment anything changes the step: gravity
            // accelerates the projectile past its own hitbox length every tick, so an arcing shot
            // leaves a widening gap in its path on the way down. Taking the delta also keeps
            // subclasses that steer in move() (the homing return legs) correct for free.
            Vector step = currentLoc.toVector().subtract(prevLoc.toVector());
            double stepLength = step.length();
            // Didn't move — no new segment to check, and rayTrace rejects a zero-length direction.
            if (stepLength < 1.0E-6) return false;

            // Both are consumed by the callbacks hitbox.get() fires synchronously below, so both are
            // written per segment. currentDirection feeds DIRECTION knockback and the trajectory the
            // Aoe backs a buried blast center out along — the wall callback had no trajectory at all
            // before this. Clearing impactLocation stops a piercing shot from carrying an old contact
            // point forward to a later expiry.
            currentDirection = step.clone().normalize();
            impactLocation = null;

            hitbox.moveTo(prevLoc).reorient(step).resize(stepLength);
            Set<LivingEntity> hits = hitbox.get();
            boolean hitWall = hitbox.getWallHitLocation() != null;
            boolean hitPlayer = !hits.isEmpty();
            // Victim contact wins when both resolve on one segment: the wall trace clamps the sweep's
            // reach, so any victim it accepted is nearer than the wall.
            if (impactLocation == null) impactLocation = hitbox.getWallHitLocation();
            return (hitWall && !piercesWalls) || (hitPlayer && !piercesPlayers);
        }

        protected void displayVisuals(Location prevLoc) {
            // Aim the model down the segment it just flew, not down `velocity`: move() has already
            // applied this tick's gravity, so the velocity field is the NEXT step's heading and the
            // nose sat one step ahead of the model's own travel on every arcing shot.
            Vector heading = currentLoc.toVector().subtract(prevLoc.toVector());
            if (heading.lengthSquared() < 1.0E-12) heading = velocity;

            if (displayEntity == null) {
                displayEntity = projVisual.spawnDisplay(currentLoc, hero.getSkin().getId());
                if (displayEntity != null) {
                    displayEntity.setInterpolationDelay(0);
                    displayEntity.setTeleportDuration(1);
                    AbstractDisplayBuilder.orient(displayEntity, heading, projVisual.modelBackOffset());
                }
            } else {
                displayEntity.teleport(AbstractDisplayBuilder.canonical(currentLoc));
                // Re-orienting every tick also refreshes the back-offset translation, which has to
                // follow the heading as gravity bends it.
                AbstractDisplayBuilder.orient(displayEntity, heading, projVisual.modelBackOffset());
            }
            // Fallback: if not an ITEM_DISPLAY type, play normally
            if (displayEntity == null) {
                projVisual.play(currentLoc, velocity, hero.getSkin().getId());
            }

            if (trailSpacingDistance > 0) {
                // Distance mode: emit at evenly-spaced sub-tick positions along the velocity vector.
                // Walks back from currentLoc by the post-decrement accumulator so multiple emissions
                // per fast tick land at correctly spaced points instead of stacking on currentLoc.
                double velocityLength = velocity.length();
                distanceTrailAccumulator += velocityLength;
                while (distanceTrailAccumulator >= trailSpacingDistance) {
                    distanceTrailAccumulator -= trailSpacingDistance;
                    Vector backOffset = velocity.clone().multiply(distanceTrailAccumulator / velocityLength);
                    Location emitLoc = currentLoc.clone().subtract(backOffset);
                    emitTrail(emitLoc);
                }
            } else {
                // Tick mode (default).
                trailTickCounter++;
                if (trailTickCounter >= trailSpacing) {
                    trailTickCounter = 0;
                    emitTrail(currentLoc);
                }
            }
        }

        /** Emit a single trail sample. Respects {@code trailPersists} — persistent emissions are tracked for cleanup. */
        protected void emitTrail(Location loc) {
            if (trailPersists) {
                trailDisplays.addAll(trailVisual.playPersistent(loc, velocity, hero.getSkin().getId()));
            } else {
                trailVisual.play(loc, velocity, hero.getSkin().getId());
            }
        }

        public void cleanup() {
            expired = true;
            if (displayEntity != null && !displayEntity.isDead()) {
                displayEntity.remove();
            }
            for (ItemDisplay d : trailDisplays) {
                if (d != null && !d.isDead()) d.remove();
            }
            trailDisplays.clear();
        }

        public Location getLocation() { return currentLoc; }
        public boolean expired() { return expired; }
    }
}
