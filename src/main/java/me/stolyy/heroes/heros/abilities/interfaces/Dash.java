package me.stolyy.heroes.heros.abilities.interfaces;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.abilities.data.DashData;
import me.stolyy.heroes.utility.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public interface Dash {
    default void dash(Player player, AbilityType abilityType, DashData dashData) {
        Location startLocation = player.getLocation();
        final Vector direction = player.getEyeLocation().getDirection();
        double dashSpeed = dashData.speed(); // Speed of the dash movement
        int maxDurationTicks = 7; // Maximum duration of the dash in ticks

        player.setVelocity(direction.clone().multiply(dashSpeed));

        new BukkitRunnable() {
            double distanceTravelled = 0.0;
            final Set<Player> nearbyPlayers = new HashSet<>();
            final HashMap<Player, Boolean> hitPlayer = new HashMap<>();
            Location lastLocation = startLocation.clone();
            int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed++;
                Location currentLocation = player.getLocation();
                distanceTravelled += lastLocation.distance(currentLocation);

                if (distanceTravelled >= dashData.distance() || ticksElapsed >= maxDurationTicks) {
                    endDash();
                    onDashEnd(currentLocation, abilityType);
                    return;
                }

                // Check for wall collision
                //if (WallDetection.detectWall(lastLocation, currentLocation, 0.5)) {
                    //endDash();
                    //return;
                //}

                //hitbox detection
                nearbyPlayers.addAll(Hitbox.cube(player.getEyeLocation().setDirection(direction), 2));
                for (Player nearbyPlayer : nearbyPlayers) {
                    if (nearbyPlayer != player && !hitPlayer.getOrDefault(nearbyPlayer, false)) {
                        onDashHit(nearbyPlayer, currentLocation, abilityType);
                        hitPlayer.put(nearbyPlayer, true);
                    }
                }

                lastLocation = currentLocation;
            }

            private void endDash() {
                player.setVelocity(new Vector(0, 0, 0)); // Reset velocity
                this.cancel();
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    void onDashHit(Player target, Location location, AbilityType abilityType);
    default void onDashEnd(Location location, AbilityType abilityType) {}
}