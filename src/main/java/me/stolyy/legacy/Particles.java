package me.stolyy.legacy;

import me.stolyy.heroes.Heroes;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Particles {
    public static void summonParticle(Location location, Particle particle, Particle.DustOptions dustOptions){
        if (particle == Particle.DUST && dustOptions != null) {
            location.getWorld().spawnParticle(Particle.DUST, location, 1, dustOptions);
        } else {
            location.getWorld().spawnParticle(particle, location, 1, 0, 0, 0, 0);
        }
    }

    public static void ring(Location location, double radius, Particle particle) {
        if (location == null || particle == null || radius <= 0) {
            return; // Invalid input
        }

        double angleIncrement = Math.PI / 16; // 16 segments for a full circle
        for (double angle = 0; angle < 2 * Math.PI; angle += angleIncrement) {
            double x = location.getX() + radius * Math.cos(angle);
            double z = location.getZ() + radius * Math.sin(angle);
            Location particleLocation = new Location(location.getWorld(), x, location.getY(), z);
            location.getWorld().spawnParticle(particle, particleLocation, 1);
        }
    }

    public static void directionalRing(Location location, double radius, Particle particle) {
        directionalRing(location, radius, particle, null);
    }

    public static void directionalRing(Location location, double radius, Particle particle, Particle.DustOptions dustOptions) {
        if (location == null || particle == null || radius <= 0) {
            return; // Invalid input
        }

        Vector up = new Vector(0, 1, 0);
        double yaw = Math.toRadians(location.getYaw());
        Vector locationForward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));

        Vector ringAxis1 = up.clone().multiply(Math.cos(Math.abs(Math.toRadians(location.getPitch()))));
        ringAxis1.add(locationForward.clone().multiply(Math.sin(Math.abs(Math.toRadians(location.getPitch())))));
        Vector ringAxis2 = new Vector(locationForward.getZ(), 0 , -locationForward.getX());


        double angleIncrement = Math.PI / 16; // 16 segments for a full circle
        for (double angle = 0; angle < 2 * Math.PI; angle += angleIncrement) {
            double axis1Component = radius * Math.cos(angle);
            double axis2Component = radius * Math.sin(angle);

            Vector relativePosition = ringAxis1.clone().multiply(axis1Component)
                    .add(ringAxis2.clone().multiply(axis2Component));
            Location particleLocation = location.clone().add(relativePosition);
            if(dustOptions != null)
                location.getWorld().spawnParticle(particle, particleLocation, 1, dustOptions);
            else
                location.getWorld().spawnParticle(particle, particleLocation, 1, 0, 0 ,0);
        }
    }

    public static void blood(Player victim) {
        new BukkitRunnable() {
            int g = 200;
            public void run() {
                if (!victim.isOnGround() && victim.getVelocity().getY() >= 0.35) {
                    for (int i = 0; i < 6; i++) {
                        if (victim.getVelocity().getY() <= 0.0) {
                            this.cancel();
                            return;
                        }
                        this.g = Math.max(this.g - 6, 0);
                        Particle.DustTransition dustOptions = new Particle.DustTransition(
                                Color.fromRGB(255, this.g, 0),  // Start color
                                Color.fromRGB(150, 0, 0),      // Transition to darker red
                                1.2f                           // Particle size
                        );
                        victim.getWorld().spawnParticle(
                                Particle.DUST_COLOR_TRANSITION,
                                victim.getLocation(),
                                1, // Must be 1 for DUST_COLOR_TRANSITION
                                0.2, 0.2, 0.2, // Slight random spread
                                dustOptions
                        );
                    }
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 2L, 1L);
    }
}
