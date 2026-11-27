package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.RideDisplay;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;

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

    public boolean isRiding() {
        return isRiding && currentProjectile != null && !currentProjectile.expired();
    }

    public Location rideLocation() {
        return isRiding() ? currentProjectile.getLocation() : null;
    }

    public Vector rideVelocity() {
        return isRiding() ? currentProjectile.getVelocity() : null;
    }

    /** Distance covered during the mandatory pre-cancel window (plus 1 tick margin). */
    public double minCancelDistance() {
        int minTicks = (int) Math.ceil(reactivationWindow / (double) Heroes.MS_PER_TICK) + 1;
        return Math.min(range, speed * minTicks);
    }

    /** Total straight-line travel distance affordable from current energy after paying activation cost. */
    public double projectedDistance(Hero hero) {
        double postCost = hero.getEnergy().getCurrentEnergy() - getEnergyCost();
        if (postCost < 0) return 0;
        return projectedForEnergy(hero, postCost, range);
    }

    /** Remaining straight-line distance an active ride can cover before running out of energy or range. */
    public double remainingProjectedDistance(Hero hero) {
        if (!isRiding()) return 0;
        double remainingRange = Math.max(0, range - currentProjectile.getDistanceTraveled());
        return projectedForEnergy(hero, hero.getEnergy().getCurrentEnergy(), remainingRange);
    }

    private double projectedForEnergy(Hero hero, double energyAvailable, double maxRange) {
        double regenPerTick = hero.getEnergy().canRegen() ? hero.getEnergy().getRegenPerTick() : 0.0;
        double netDrain = energyPerBlock - (regenPerTick / speed);
        if (netDrain <= 0) return maxRange;
        return Math.min(maxRange, Math.max(0, energyAvailable / netDrain));
    }

    @Override
    protected boolean canRecast(Hero hero) {
        return isRiding;
    }

    @Override
    protected boolean tryRecast(Hero hero) {
        if (System.currentTimeMillis() - lastToggle > reactivationWindow) {
            dismount(hero);
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
    protected ProjectileInstance createInstance(Hero hero, Location start, ShotParams shot) {
        RideableInstance rideable = new RideableInstance(hero, start, shot);
        this.currentProjectile = rideable;
        return rideable;
    }

    @Override
    protected void onInstanceEnded(Hero hero, ProjectileInstance instance) {
        if (instance == currentProjectile) {
            dismount(hero);
        }
    }

    @Override
    protected void onProjectileTick(Hero hero) {
        if (!isRiding) return;

        if (currentProjectile == null || currentProjectile.expired() || currentProjectile.isRiderGone()) {
            dismount(hero);
            return;
        }

        Location currentLoc = currentProjectile.getLocation();
        double distance = currentLoc.distance(lastEnergyLoc);
        if (distance >= 1.0) {
            float cost = (float) (distance * energyPerBlock);
            if (!hero.getEnergy().tryConsume(hero.getController(), cost)) {
                dismount(hero);
                return;
            }
            lastEnergyLoc = currentLoc.clone();
        }
    }

    private void dismount(Hero hero) {
        if (!isRiding) return;
        isRiding = false;
        if (currentProjectile != null) {
            currentProjectile.cleanup();
            currentProjectile = null;
        }
        resolveCooldown(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        dismount(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        dismount(hero);
    }

    private class RideableInstance extends ProjectileInstance {
        private RideDisplay ride;

        RideableInstance(Hero hero, Location start, ShotParams shot) {
            super(hero, start, shot);
            ItemDisplay spawned = projVisual.spawnDisplay(start, hero.getSkin().getId());
            this.ride = spawned != null ? RideDisplay.mount(hero, spawned) : null;
        }

        boolean isRiderGone() {
            if (ride != null) return ride.isRiderGone(hero);
            return hero.getController() == null || hero.getController().getEntity() == null
                    || hero.getController().getEntity().isDead() || hero.isRespawning();
        }

        Vector getVelocity() {
            return velocity;
        }

        double getDistanceTraveled() {
            return distanceTraveled;
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
