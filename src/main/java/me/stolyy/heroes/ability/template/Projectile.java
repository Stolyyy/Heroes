package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Homing;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tick-stepped projectile template supporting gravity, Velocity-Verlet stepping, homing steering,
 * boomerangs, spinning item displays, and independent flight across owner elimination.
 */
public class Projectile extends RangedAbility {
    protected double speed;
    protected double gravity;
    protected boolean returns;
    protected double catchRadius;
    protected double returnRangeMult;
    protected double spinDegPerTick;

    /**
     * Hard ceiling on one instance's lifetime, in ticks. ceil(range/speed) is the shortest a flight
     * can possibly be — {@link ProjectileInstance#move()} credits at most one tick of muzzle speed
     * against the range budget — so this is six times the ideal flight plus two seconds. That sits
     * comfortably above the slowest legitimate flight: a straight-up lob loiters an extra
     * ~speed/gravity ticks around its apex before it is back up to muzzle speed, ~25 for the
     * skullfire grenade against an ideal 38. Nothing the range test already expires can reach it;
     * it exists for the subclasses with conditional exits (boomerang, saber, ricochet) and for
     * degenerate launches such as Defecake's straight-down (0,-1,0) throw. The cast saturates, so a
     * config with speed 0 yields Integer.MAX_VALUE — effectively no cap — rather than an overflowed
     * negative one that would kill every shot on its first tick.
     */
    protected final int maxLifetimeTicks;

    protected VisualEffect projVisual;

    protected final List<ProjectileInstance> activeProjectiles = new ArrayList<>();

    public Projectile(ConfigurationSection config, HeroSkin skin) {
        this(config, skin, null);
    }

    public Projectile(ConfigurationSection config, HeroSkin skin, AbilityType explicitType) {
        super(config, skin, explicitType);
        this.speed = Configs.orTemplate(this.config, "speed", "projectile.speed");
        this.gravity = Configs.orTemplate(this.config, "gravity", "projectile.gravity");
        this.returns = Configs.orTemplateBoolean(this.config, "returns", "projectile.returns");
        this.catchRadius = Configs.orTemplate(this.config, "catch_radius", "projectile.catch_radius");
        this.returnRangeMult = Configs.orTemplate(this.config, "return_range_mult", "projectile.return_range_mult");
        this.spinDegPerTick = Configs.orTemplate(this.config, "spin_deg_per_tick", "projectile.spin_deg_per_tick");
        this.maxLifetimeTicks = (int) (Math.ceil(range / speed) * 6 + 40);
        this.projVisual = VisualEffect.optional(this.config, "visuals.projectile_visual");

        if (!this.config.contains("visuals.trail_spacing")) {
            this.trailSpacing = Configs.orTemplate(this.config, "visuals.trail_spacing", "projectile.trail_spacing");
        }
    }

    @Override
    protected ShotParams shotParamsFor(Hero hero) {
        return new ShotParams(null, null, speed, fireSound, trailVisual, hitVisual);
    }

    protected ProjectileInstance createInstance(Hero hero, Location start, ShotParams shot) {
        return new ProjectileInstance(hero, start, shot);
    }

    protected void onInstanceEnded(Hero hero, ProjectileInstance instance) {}

    @Override
    protected void fireSingleShot(Hero hero) {
        ShotParams shot = shotParamsFor(hero);
        shot.fireSound().play(hero.getController().getLocation(), hero.getController());
        activeProjectiles.add(createInstance(hero, getStartLocation(hero), shot));
    }

    @Override
    protected final void onRangedTick(Hero hero) {
        // Iterate a snapshot: a detonation that scores the winning kill tears the game down
        // synchronously inside tick(), which reaches onOwnerLost -> activeProjectiles.clear().
        // A removeIf over the live list would blow up with ConcurrentModificationException.
        for (ProjectileInstance p : new ArrayList<>(activeProjectiles)) {
            if (p.tick()) activeProjectiles.remove(p);
        }
        onProjectileTick(hero);
    }

    protected void onProjectileTick(Hero hero) {}

    /** In-flight projectiles are detached world state — they keep ticking while the owner is dormant. */
    @Override
    public boolean isIndependent() { return true; }

    /** Owner reference lost (logout/game end): expire all in-flight instances and their displays. */
    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        for (ProjectileInstance p : new ArrayList<>(activeProjectiles)) {
            p.cleanup();
        }
        activeProjectiles.clear();
    }

    protected class ProjectileInstance {
        protected final Hero hero;
        protected final ShotParams shot;
        protected final Location currentLoc;
        protected final Vector velocity;
        protected double distanceTraveled = 0;
        protected int trailTickCounter = 0;
        /** Distance traveled since the last trail emission. Used when {@code trailSpacingDistance > 0}. */
        protected double distanceTrailAccumulator = 0;
        protected final Hitbox hitbox;
        protected boolean expired = false;
        protected boolean returning = false;
        protected double returnDistance = 0;
        protected Homing returnHoming;
        protected float spinRadians = 0;
        /** Ticks this instance has been alive, bounded by {@link #maxLifetimeTicks()}. */
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
            this(hero, start, shotParamsFor(hero));
        }

        public ProjectileInstance(Hero hero, Location start, ShotParams shot) {
            this(hero, start, getDirection(start), shot);
        }

        public ProjectileInstance(Hero hero, Location start, Vector direction, ShotParams shot) {
            this.hero = hero;
            this.shot = shot;
            this.currentLoc = start.clone();
            this.velocity = direction.clone().normalize().multiply(shot.speed());
            this.hitbox = Hitbox.cylinder(start, velocity, radius, velocity.length())
                    .exclude(hero.getController().getEntity())
                    .excludeTeammates(hero.getController().getEntity())
                    .checkWalls(!piercesWalls)
                    .pierce(piercesPlayers)
                    .trackHits()
                    .onHit((victim, loc) -> {
                        lastHitDistance = distanceTraveled;
                        impactLocation = loc;
                        withActiveShot(shot, () -> {
                            onHit(hero, victim, loc);
                            onVictimHit(victim, loc);
                        });
                    });
        }

        protected int maxLifetimeTicks() { return maxLifetimeTicks; }
        protected double flightRange() { return range; }
        protected Location returnAnchor() { return hero.getController().getEyeLocation(); }
        protected boolean returnPiercesWalls() { return piercesWalls; }
        protected boolean clearHitsOnReturn() { return true; }
        protected boolean shouldStartReturn() { return distanceTraveled >= flightRange(); }
        protected boolean steer() { return true; }
        protected boolean onBounce(@Nullable BlockFace face, Location wallLoc) { return false; }
        protected void onReturnStart() {}
        protected void onCaught() {}
        protected void onVictimHit(LivingEntity victim, Location hitLoc) {}
        public boolean isReturning() { return returning; }

        public void startReturn() {
            if (returning) return;
            returning = true;
            if (clearHitsOnReturn()) hitbox.clearHitHistory();
            if (returnPiercesWalls()) hitbox.checkWalls(false);
            returnHoming = new Homing(hero.getController().getEntity(), shot.speed(), () -> null);
            onReturnStart();
        }

        public boolean tick() {
            if (expired) return true;
            if (overLifetime()) return true;
            if (returns && !returning && shouldStartReturn()) {
                startReturn();
            }

            Location prevLoc = currentLoc.clone();
            if (!move()) {
                if (!expired) {
                    withActiveShot(shot, () -> onExpire(hero, currentLoc));
                    finish(prevLoc);
                }
                return true;
            }
            if (returning && (currentLoc.distanceSquared(returnAnchor()) < catchRadius * catchRadius
                    || returnDistance > flightRange() * returnRangeMult)) {
                onCaught();
                cleanup();
                return true;
            }

            // Collisions resolve BEFORE the range test so the final segment is always swept.
            if (checkCollisions(prevLoc)) {
                finish(prevLoc);
                return true;
            }

            if (!returns && distanceTraveled > flightRange()) {
                withActiveShot(shot, () -> onExpire(hero, currentLoc));
                finish(prevLoc);
                return true;
            }

            displayVisuals(prevLoc);
            return false;
        }

        /**
         * Hard lifetime backstop and practice-arena fence. Terminates silently without firing
         * onExpire so conditional-exit projectiles never detonate on a lifetime cap.
         */
        protected boolean overLifetime() {
            if (++ticksAlive <= maxLifetimeTicks() && !leftPracticeArena()) return false;
            cleanup();
            return true;
        }

        private boolean leftPracticeArena() {
            return hero.getController().getEntity() instanceof Player owner
                    && PracticeManager.isPracticing(owner)
                    && !PracticeManager.arenaOf(owner).contains(currentLoc);
        }

        /**
         * Last visible frame, then teardown. Places the display at the resolved endpoint first and
         * removes it 2 ticks later so the client interpolation finishes on the actual impact point.
         */
        protected void finish(Location prevLoc) {
            Location end = impactLocation != null ? impactLocation : currentLoc;
            if (displayEntity != null && !displayEntity.isDead()) {
                Vector heading = end.toVector().subtract(prevLoc.toVector());
                if (spinDegPerTick != 0) {
                    spinRadians += (float) Math.toRadians(spinDegPerTick);
                    AbstractDisplayBuilder.follow(displayEntity, end, heading, projVisual.modelBackOffset(), spinRadians);
                } else {
                    AbstractDisplayBuilder.follow(displayEntity, end, heading, projVisual.modelBackOffset());
                }
                ItemDisplay lastFrame = displayEntity;
                displayEntity = null; // cleanup() must not undo the frame just drawn
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), lastFrame::remove, 2L);
            }
            cleanup();
        }

        protected boolean move() {
            if (returning) {
                Vector step = returnHoming.step(currentLoc);
                if (step == null) {
                    cleanup();
                    return false;
                }
                velocity.copy(step);
                currentLoc.add(step);
                returnDistance += step.length();
                distanceTraveled += Math.min(step.length(), shot.speed());
                return true;
            }
            if (!steer()) return false;

            // Velocity-Verlet: half a tick of gravity before the step, half after.
            velocity.setY(velocity.getY() - gravity / 2);
            currentLoc.add(velocity);
            // Credit at most one tick of muzzle speed so lobbed shots don't burn range faster as gravity builds Y.
            distanceTraveled += Math.min(velocity.length(), shot.speed());
            velocity.setY(velocity.getY() - gravity / 2);
            return true;
        }

        protected boolean checkCollisions(Location prevLoc) {
            Vector step = currentLoc.toVector().subtract(prevLoc.toVector());
            double stepLength = step.length();
            if (stepLength < 1.0E-6) return false;

            currentDirection = step.clone().normalize();
            impactLocation = null;

            hitbox.moveTo(prevLoc).reorient(step).resize(stepLength);
            Set<LivingEntity> hits = hitbox.get();
            Location wallLoc = hitbox.getWallHitLocation();
            BlockFace wallFace = hitbox.getWallHitFace();
            if (impactLocation == null) impactLocation = wallLoc;

            boolean stoppedByWall = false;
            if (wallLoc != null && (hits.isEmpty() || piercesPlayers)) {
                if (onBounce(wallFace, wallLoc)) {
                    currentLoc.setX(wallLoc.getX());
                    currentLoc.setY(wallLoc.getY());
                    currentLoc.setZ(wallLoc.getZ());
                } else {
                    withActiveShot(shot, () -> onHitWall(hero, wallLoc));
                    if (returns && !returning) {
                        currentLoc.setX(wallLoc.getX());
                        currentLoc.setY(wallLoc.getY());
                        currentLoc.setZ(wallLoc.getZ());
                        startReturn();
                    } else if (!piercesWalls) {
                        stoppedByWall = true;
                    }
                }
            }
            return stoppedByWall || (!hits.isEmpty() && !piercesPlayers);
        }

        protected void displayVisuals(Location prevLoc) {
            Vector heading = currentLoc.toVector().subtract(prevLoc.toVector());
            if (heading.lengthSquared() < 1.0E-12) heading = velocity;

            if (displayEntity == null) {
                displayEntity = projVisual.spawnDisplay(currentLoc, hero.getSkin().getId());
                if (displayEntity != null) {
                    if (spinDegPerTick != 0) {
                        spinRadians = (float) Math.toRadians(spinDegPerTick);
                        AbstractDisplayBuilder.orient(displayEntity, heading, projVisual.modelBackOffset(), spinRadians);
                    } else {
                        AbstractDisplayBuilder.orient(displayEntity, heading, projVisual.modelBackOffset());
                    }
                }
            } else {
                if (spinDegPerTick != 0) {
                    spinRadians += (float) Math.toRadians(spinDegPerTick);
                    AbstractDisplayBuilder.follow(displayEntity, currentLoc, heading, projVisual.modelBackOffset(), spinRadians);
                } else {
                    AbstractDisplayBuilder.follow(displayEntity, currentLoc, heading, projVisual.modelBackOffset());
                }
            }
            // Fallback: if not an ITEM_DISPLAY type, play normally
            if (displayEntity == null) {
                projVisual.play(currentLoc, velocity, hero.getSkin().getId());
            }

            if (trailSpacingDistance > 0) {
                double velocityLength = velocity.length();
                if (velocityLength > 1.0E-6) {
                    distanceTrailAccumulator += velocityLength;
                    while (distanceTrailAccumulator >= trailSpacingDistance) {
                        distanceTrailAccumulator -= trailSpacingDistance;
                        Vector backOffset = velocity.clone().multiply(distanceTrailAccumulator / velocityLength);
                        Location emitLoc = currentLoc.clone().subtract(backOffset);
                        emitTrail(emitLoc);
                    }
                }
            } else {
                trailTickCounter++;
                if (trailTickCounter >= trailSpacing) {
                    trailTickCounter = 0;
                    emitTrail(currentLoc);
                }
            }
        }

        /** Emit a single trail sample. Respects {@code trailPersists} — persistent emissions are tracked for cleanup. */
        protected void emitTrail(Location loc) {
            VisualEffect tv = shot.trailVisual();
            if (trailPersists) {
                trailDisplays.addAll(tv.playPersistent(loc, velocity, hero.getSkin().getId()));
            } else {
                tv.play(loc, velocity, hero.getSkin().getId());
            }
        }

        public void cleanup() {
            if (expired) return;
            expired = true;
            if (displayEntity != null && !displayEntity.isDead()) {
                displayEntity.remove();
            }
            for (ItemDisplay d : trailDisplays) {
                if (d != null && !d.isDead()) d.remove();
            }
            trailDisplays.clear();
            onInstanceEnded(hero, this);
        }

        public boolean expired() { return expired; }
        public Location getLocation() { return currentLoc; }
    }
}
