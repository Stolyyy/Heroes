package me.stolyy.heroes.heros.abilities.interfaces;

import io.papermc.paper.entity.TeleportFlag;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.abilities.data.ProjectileData;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.utility.effects.ArmorStands;
import me.stolyy.heroes.utility.physics.Hitbox;
import me.stolyy.heroes.utility.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.LinkedHashSet;
import java.util.Set;

public interface Projectile {
    double GRAVITY_ACCELERATION = 0.098;

    default ArmorStand projectile(Player player, AbilityType abilityType, ProjectileData projectileData) {
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection().clone();
        ArmorStand armorStand = ArmorStands.summonArmorStand(location, projectileData.customModelData());



        Set<Player> hitPlayers = new LinkedHashSet<>();
        Set<Player> alreadyHitPlayers = new LinkedHashSet<>();

        new BukkitRunnable(){
            Location currentLocation = location.clone();
            double distanceTraveled = 0;
            final Vector velocity = direction.clone().multiply(projectileData.speed());

            @Override
            public void run() {
                hitPlayers.addAll(Hitbox.sphere(currentLocation.add(0,1.85,0), projectileData.radius()));
                hitPlayers.remove(player);

                if (projectileData.gravity())
                    velocity.setY(velocity.getY() - GRAVITY_ACCELERATION);
                Location nextLocation = currentLocation.clone().add(velocity);

                if(armorStand.isDead() || !armorStand.isValid()) {
                    this.cancel();
                    return;
                } else if(WallDetection.rayCast(currentLocation.clone().add(0,1.85,0), nextLocation.clone().add(0,1.85, 0)) || distanceTraveled >= projectileData.range()) {
                    onProjectileHitWall(currentLocation, abilityType);
                    armorStand.remove();
                    this.cancel();
                    return;
                } else if(!projectileData.piercesPlayers() && !hitPlayers.isEmpty()) {
                    armorStand.remove();
                    this.cancel();
                    return;
                }

                for (Player hitPlayer : hitPlayers) {
                    if (alreadyHitPlayers.contains(hitPlayer)) continue;
                    alreadyHitPlayers.add(hitPlayer);
                    onProjectileHit(hitPlayer, currentLocation, abilityType);
                }

                distanceTraveled += currentLocation.distance(nextLocation);
                currentLocation = nextLocation;
                armorStand.teleport(currentLocation, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
        return armorStand;
    }

    void onProjectileHit(Player target, Location location, AbilityType abilityType);
    default void onProjectileHitWall(Location location, AbilityType abilityType) {}
}