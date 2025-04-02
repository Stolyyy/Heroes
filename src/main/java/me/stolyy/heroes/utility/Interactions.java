package me.stolyy.heroes.utility;

import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.Hero;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class Interactions {
    public static Map<Player, Integer> mitigation = new HashMap<>();
    //TODO: add hitbox detect in here, add static KB (nonscaling with hp)

    public static void handleInteractions(Vector vector, double knockback, double damage, Player attacker, Player victim) {
        handleInteractions(attacker.getLocation(), attacker.getLocation().clone().add(vector), knockback, damage, attacker, victim);
    }

    public static void handleInteractions(Location location, double knockback, double damage, Player attacker, Player victim) {
        handleInteractions(victim.getLocation(), location, knockback, damage, attacker, victim);
    }

    public static void handleInteractions(Location sourceLocation, Location hitLocation, double knockback, double damage, Player damager, Player target) {
        handleInteractions(sourceLocation, hitLocation, knockback, 1, damage, false, false, damager, target);
    }

    public static void handleInteractions(Location sourceLocation, Location hitLocation, double knockback, double verticalKnockback, double damage, Player damager, Player target) {
        handleInteractions(sourceLocation, hitLocation,  knockback, verticalKnockback, damage, false, false, damager, target);
    }

    //idk what is increasing
    //hitscan is NOT directional, projectiles are
    public static void handleInteractions(Location sourceLocation, Location hitLocation, double knockback, double verticalKnockback, double damage, boolean directionByLocations, boolean increasing, Player damager, Player target) {
        if(GameManager.getPlayerGame(damager) != GameManager.getPlayerGame(target)){
            return;
        } else if(GameManager.getPlayerGame(damager).getPlayerTeams().get(damager).equals(GameManager.getPlayerGame(target).getPlayerTeams().get(target))){
            return;
        }
        blood(target);
        target.setVelocity(handleKnockback(sourceLocation, hitLocation, knockback, verticalKnockback, directionByLocations, increasing, target));
        handleDamage(damage, damager, target);
    }

    private static Vector handleKnockback(Location sourceLocation, Location hitLocation, double knockback, double verticalKnockback, boolean directionByLocations, boolean increasing, Player target) {
        Hero targetHero = HeroManager.getHero(target);
        Vector check = target.getVelocity();

        if (directionByLocations) {
            Location targetLocation = target.getLocation();
            hitLocation.setDirection(targetLocation.subtract(sourceLocation).toVector());
        }

        double x = check.getX();
        double xReduction = 0.0;
        double y = check.getY();
        double yReduction = 0.0;
        double z = check.getZ();
        double zReduction = 0.0;
        if (increasing) {
            for (double i = -0.1; i > x; i -= 0.2) xReduction += 0.05;
            if (xReduction >= verticalKnockback) return target.getVelocity();
            for (double i = -0.4; i > y; i -= 0.2) yReduction += 0.05;
            if (yReduction >= verticalKnockback) return target.getVelocity();
            for (double i = -0.1; i > z; i -= 0.2) zReduction += 0.05;
            if (zReduction >= verticalKnockback) return target.getVelocity();
        }

        target.setVelocity(new Vector(0, 0, 0));
        double finalKnockback = knockback * 0.8 / 5 - yReduction;
        double finalVerticalKnockback = verticalKnockback * 0.8 - yReduction;
        double addKnockback = knockback * 0.0325;
        double addVerticalKnockback = verticalKnockback * 0.0325;
        if (increasing) {
            for (double t = target.getMaxHealth(); t > target.getHealth(); t -= 1.0) {
                finalKnockback += addKnockback;
                finalVerticalKnockback += addVerticalKnockback;
            }
        }
        float pitch = hitLocation.getPitch();
        if (!target.getLocation().getBlock().getRelative(0, -1, 0).getType().isAir()) {
            if (pitch >= -10.0f) {
                pitch = -15.0f;
            }
        } else if (pitch >= -10.0f && pitch <= 80.0f) {
            pitch -= 10.0f;
        } else if (pitch <= -15.0f && pitch >= -80.0f) {
            float pr = 10.0f;
            pitch += pr;
        }
        hitLocation.setPitch(pitch);
        Vector direction = hitLocation.getDirection();
        double weight = targetHero.weight / 3;
        direction.setY(direction.getY() * (double)(1.0f - Math.abs(pitch) / 90.0f));
        Vector kb = target.getVelocity().multiply(0.8).add(direction.multiply(weight + finalKnockback * (1.0 - mitigation.getOrDefault(target,0))));
        kb.setY(kb.getY() - weight - finalVerticalKnockback * Math.sin(Math.toRadians(pitch)) * (1.0 - mitigation.getOrDefault(target,0)));
        return kb;
    }

    private static void handleDamage(double damage, Player damager, Player target) {
        double currentHealth = target.getHealth();
        target.damage(1);
        if (currentHealth > 1.0) {
            target.setHealth(Math.max(1.0, currentHealth - damage));
        }
    }

    public static void blood(final Player p) {
        new BukkitRunnable() {
            int g = 200;
            public void run() {
                if (!p.isOnGround() && p.getVelocity().getY() >= 0.35) {
                    for (int i = 0; i < 6; i++) {
                        if (p.getVelocity().getY() <= 0.0) {
                            this.cancel();
                            return;
                        }
                        this.g = Math.max(this.g - 6, 0);
                        Particle.DustTransition dustOptions = new Particle.DustTransition(
                                Color.fromRGB(255, this.g, 0),  // Start color
                                Color.fromRGB(150, 0, 0),      // Transition to darker red
                                1.2f                           // Particle size
                        );
                        p.getWorld().spawnParticle(
                                Particle.DUST_COLOR_TRANSITION,
                                p.getLocation(),
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