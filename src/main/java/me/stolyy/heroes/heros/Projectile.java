package me.stolyy.heroes.heros;

import io.papermc.paper.entity.TeleportFlag;
import me.stolyy.heroes.Hero;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.WallDetection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public interface Projectile {
    static ArmorStand projectile(Player player, double speed, boolean hasGravity, Material block, double radius, Hero hero, AbilityType abilityType) {
        Location location = player.getLocation();
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();

        ArmorStand armorStand = (ArmorStand) eyeLocation.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        armorStand.getEquipment().setHelmet(ItemStack.of(block));
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);

        final Location startLocation = eyeLocation.clone();

        new BukkitRunnable() {
            private double distanceTraveled = 0;
            private Location lastLocation = startLocation.clone();
            private final double gravityAcceleration = 0.05;
            private Vector velocity = direction.clone().multiply(speed);

            @Override
            public void run() {
                if (armorStand.isDead() || !armorStand.isValid()) {
                    this.cancel();
                    return;
                }

                if (hasGravity) {
                    velocity.setY(velocity.getY() - gravityAcceleration);
                }

                Location newLocation = armorStand.getLocation().add(velocity);
                distanceTraveled += newLocation.distance(lastLocation);

                Location checkLocation = newLocation.clone().add(0, 2, 0);
                if (WallDetection.detectWall(lastLocation.clone().add(0, 2, 0), checkLocation, radius) || distanceTraveled >= 100) {
                    ((Projectile) hero).onProjectileHitWall(armorStand.getLocation(), abilityType);
                    armorStand.remove();
                    this.cancel();
                    return;
                }

                List<Player> nearbyPlayers = (List<Player>) newLocation.getWorld().getNearbyPlayers(newLocation, radius);
                for (Player target : nearbyPlayers) {
                    if (target != player) {
                        ((Projectile) hero).onProjectileHit(target, armorStand.getLocation(), abilityType);
                        armorStand.remove();
                        this.cancel();
                        return;
                    }
                }

                armorStand.teleport(newLocation, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                lastLocation = newLocation;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);

        return armorStand;
    }

    void onProjectileHit(Player target, Location location, AbilityType abilityType);
    void onProjectileHitWall(Location location, AbilityType abilityType);
}