package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.RideDisplay;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;

public class ShoopRTL extends Projectile {
    private boolean isRiding = false;
    private long lastToggle = 0;
    private final int reactivationWindow;
    private final float energyPerBlock;
    private RideableInstance currentProjectile;
    private Location lastEnergyLoc;

    public ShoopRTL(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.reactivationWindow = Configs.requireInt(this.config, "reactivation_window_ms");
        this.energyPerBlock = (float) Configs.requireDouble(this.config, "energy_per_block");
    }

    @Override
    protected boolean canRecast(Hero hero) {
        return isRiding;
    }

    @Override
    protected boolean tryRecast(Hero hero) {
        if (System.currentTimeMillis() - lastToggle > reactivationWindow) {
            dismount(hero, true);
            return true;
        }
        return false;
    }

    @Override
    public void perform(Hero hero) {
        isRiding = true;
        lastToggle = System.currentTimeMillis();
        this.lastEnergyLoc = hero.getController().getEyeLocation().clone();
        super.perform(hero);
    }

    @Override
    protected Location getStartLocation(Hero hero) {
        Location eye = hero.getController().getEyeLocation();
        Location feet = hero.getController().getLocation();
        LivingEntity entity = hero.getController().getEntity();
        double width = entity.getWidth();
        double height = entity.getHeight();
        World world = eye.getWorld();

        if (WallDetection.isStandingClear(world, eye, width, height)) {
            return eye;
        }
        for (double y = eye.getY() - 0.1; y >= feet.getY(); y -= 0.1) {
            Location probe = eye.clone();
            probe.setY(y);
            if (WallDetection.isStandingClear(world, probe, width, height)) {
                return probe;
            }
        }
        Location fallback = feet.clone();
        fallback.setYaw(eye.getYaw());
        fallback.setPitch(eye.getPitch());
        return fallback;
    }

    @Override
    protected ProjectileInstance createInstance(Hero hero, Location start, ShotParams shot) {
        RideableInstance rideable = new RideableInstance(hero, start, shot);
        this.currentProjectile = rideable;
        return rideable;
    }

    @Override
    protected void onInstanceEnded(Hero hero, ProjectileInstance instance) {
        if (instance == currentProjectile) {
            dismount(hero, true);
        }
    }

    @Override
    protected void onProjectileTick(Hero hero) {
        if (!isRiding) return;

        if (currentProjectile == null || currentProjectile.expired() || currentProjectile.isRiderGone()) {
            dismount(hero, false);
            return;
        }

        Location currentLoc = currentProjectile.getLocation();
        double distance = currentLoc.distance(lastEnergyLoc);
        if (distance >= 1.0) {
            float cost = (float) (distance * energyPerBlock);
            if (!hero.getEnergy().tryConsume(hero.getController(), cost)) {
                dismount(hero, true);
                return;
            }
            lastEnergyLoc = currentLoc.clone();
        }
    }

    private void dismount(Hero hero, boolean reposition) {
        if (!isRiding) return;
        isRiding = false;
        Location safeLoc = reposition && currentProjectile != null
                ? currentProjectile.resolveSafeDismountLocation()
                : null;
        if (currentProjectile != null) {
            currentProjectile.cleanup();
            currentProjectile = null;
        }
        if (safeLoc != null) {
            Location current = hero.getController().getLocation();
            safeLoc.setYaw(current.getYaw());
            safeLoc.setPitch(current.getPitch());
            hero.getController().teleport(safeLoc);
        }
        resolveCooldown(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        dismount(hero, false);
        super.onDeath(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        // Dismount without repositioning before super cleans up activeProjectiles and fires onInstanceEnded.
        dismount(hero, false);
        super.onOwnerLost(hero);
    }

    private class RideableInstance extends ProjectileInstance {
        private RideDisplay ride;
        private final double riderWidth;
        private final double riderHeight;
        private Location lastSafeLoc;

        RideableInstance(Hero hero, Location start, ShotParams shot) {
            super(hero, start, shot);
            LivingEntity entity = hero.getController().getEntity();
            this.riderWidth = entity.getWidth();
            this.riderHeight = entity.getHeight();
            this.lastSafeLoc = start.clone();
            ItemDisplay spawned = projVisual.spawnDisplay(start, hero.getSkin().getId());
            this.ride = spawned != null ? RideDisplay.mount(hero, spawned) : null;
        }

        boolean isRiderGone() {
            if (ride != null) return ride.isRiderGone(hero);
            return hero.getController() == null || hero.getController().getEntity() == null
                    || hero.getController().getEntity().isDead() || hero.isRespawning();
        }

        @Override
        protected boolean checkCollisions(Location prevLoc) {
            Location safeStop = WallDetection.sweepClearPath(
                    prevLoc.getWorld(), prevLoc, currentLoc, riderWidth, riderHeight, 0.1);
            if (safeStop != null) {
                currentLoc.setX(safeStop.getX());
                currentLoc.setY(safeStop.getY());
                currentLoc.setZ(safeStop.getZ());
                lastSafeLoc = safeStop.clone();
                boolean superStopped = super.checkCollisions(prevLoc);
                if (!superStopped && !expired) {
                    withActiveShot(shot, () -> onHitWall(hero, safeStop));
                }
                return true;
            }
            lastSafeLoc = currentLoc.clone();
            return super.checkCollisions(prevLoc);
        }

        Location resolveSafeDismountLocation() {
            World world = currentLoc.getWorld();
            if (WallDetection.isStandingClear(world, currentLoc, riderWidth, riderHeight)) {
                return currentLoc.clone();
            }
            if (velocity != null && velocity.lengthSquared() > 1.0E-6) {
                Location backed = WallDetection.retreatBoxFromSolid(
                        world, currentLoc, velocity.clone().multiply(-1), 2.5, 0.1, riderWidth, riderHeight);
                if (backed != null) return backed;
            }
            return lastSafeLoc.clone();
        }

        @Override
        protected void displayVisuals(Location prevLoc) {
            if (ride != null) {
                ride.moveTo(currentLoc);
            }
            trailTickCounter++;
            if (trailTickCounter >= trailSpacing) {
                trailTickCounter = 0;
                emitTrail(currentLoc);
            }
        }

        @Override
        public void cleanup() {
            if (expired) return;
            if (ride != null) {
                ride.dismountAndRemove(hero);
                ride = null;
            }
            super.cleanup();
        }
    }
}
