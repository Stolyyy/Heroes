package me.stolyy.heroes.heros;

import me.stolyy.heroes.Hero;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.WallDetection;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;

public interface Dash {
    static void onDash(Player player, Hero hero, AbilityType abilityType, double length) {
        Location startLocation = player.getLocation();
        Vector direction = player.getEyeLocation().getDirection();
        double dashSpeed = 3.5; // Speed of the dash movement
        int maxDurationTicks = 6; // Maximum duration of the dash in ticks (adjust as needed)

        // Set initial velocity to start the dash
        player.setVelocity(direction.multiply(dashSpeed));

        new BukkitRunnable() {
            double distanceTravelled = 0.0;
            HashMap<Player, Boolean> hitPlayer = new HashMap<>();
            Location lastLocation = startLocation.clone();
            int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed++;
                Location currentLocation = player.getLocation();
                distanceTravelled += lastLocation.distance(currentLocation);

                if (distanceTravelled >= length || ticksElapsed >= maxDurationTicks) {
                    endDash();
                    return;
                }

                // Check for wall collision
                if (WallDetection.detectWall(lastLocation, currentLocation, 0.5)) {
                    endDash();
                    return;
                }

                List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(currentLocation, 1.5);
                for (Player nearbyPlayer : nearbyPlayers) {
                    if (nearbyPlayer != player && !hitPlayer.getOrDefault(nearbyPlayer, false)) {
                        ((Dash) hero).onDashHit(nearbyPlayer, currentLocation, abilityType);
                        hitPlayer.put(nearbyPlayer, true);
                    }
                }

                lastLocation = currentLocation;
            }

            private void endDash() {
                player.setVelocity(new Vector(0, 0, 0)); // Reset velocity
                this.cancel(); // Stop the task
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    void onDashHit(Player target, Location location, AbilityType abilityType);
}