package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.RideDisplay;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;

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
