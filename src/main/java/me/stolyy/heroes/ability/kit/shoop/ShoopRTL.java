package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class ShoopRTL extends Projectile {
    private boolean isRiding = false;
    private long lastToggle = 0;
    private final int reactivationWindow;
    private final float energyPerBlock;
    private ProjectileInstance currentProjectile;
    private Location lastEnergyLoc;

    public ShoopRTL(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.reactivationWindow = config.getInt("reactivation_window");
        this.energyPerBlock = (float) config.getDouble("energy_per_block");
    }

    @Override
    public boolean execute(Hero hero) {
        if (isRiding) {
            if (System.currentTimeMillis() - lastToggle > reactivationWindow) {
                dismount(hero);
            }
            return false;
        }

        if (super.execute(hero)) {
            isRiding = true;
            lastToggle = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    public void perform(Hero hero) {
        super.perform(hero);
        LivingEntity player = hero.getController().getEntity();
        Location start = player.getEyeLocation();

        fireSound.play(start);
        RideableInstance rideable = new RideableInstance(hero, start);
        activeProjectiles.add(rideable);
        this.currentProjectile = rideable;
        this.lastEnergyLoc = start.clone();

        if (rideable.display != null) {
            rideable.display.addPassenger(player);
        }
    }

    @Override
    protected void onTick(Hero hero) {
        if (isRiding) {
            LivingEntity rider = hero.getController().getEntity();
            if (rider == null || rider.isDead()
                    || (rider instanceof Player p && GameManager.isRespawning(p))) {
                dismount(hero);
            }
        }

        super.onTick(hero);

        if (!isRiding || currentProjectile == null) return;

        if (currentProjectile.expired()) {
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
        if (currentProjectile instanceof RideableInstance rideable && rideable.display != null) {
            LivingEntity rider = hero.getController().getEntity();
            if (rider != null) rider.leaveVehicle();
        }
        if (currentProjectile != null) {
            currentProjectile.cleanup();
        }
        currentProjectile = null;
        cooldown.start(activeDuration);
    }

    private class RideableInstance extends ProjectileInstance {
        private ItemDisplay display;

        public RideableInstance(Hero hero, Location start) {
            super(hero, start);
            this.display = projVisual.spawnDisplay(start, hero.getSkin().getId());
            if (display != null) {
                display.setInterpolationDelay(0);
                display.setTeleportDuration(1);
            }
        }

        @Override
        protected void displayVisuals(Location prevLoc) {
            if (display != null) {
                display.teleport(AbstractDisplayBuilder.canonical(currentLoc));
            }
            trailTickCounter++;
            if (trailTickCounter >= trailSpacing) {
                trailTickCounter = 0;
                trailVisual.play(currentLoc, velocity, hero.getSkin().getId());
            }
        }

        @Override
        public void cleanup() {
            if (display != null) {
                display.remove();
                display = null;
            }
            super.cleanup();
        }
    }
}