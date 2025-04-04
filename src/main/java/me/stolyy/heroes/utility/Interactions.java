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
    public static Map<Player, Integer> dmgMitigation = new HashMap<>();
    public static Map<Player, Integer> kbMitigation = new HashMap<>();

    //Normal knockback
    //Calculates direction based on attacker + victim locations
    public static void handleInteraction(double damage, double knockback, Player attacker, Player victim){
        Vector direction = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        handleKnockback(direction,knockback,knockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    //Calculates direction based on direction vector
    public static void handleInteraction(Vector direction, double damage, double knockback, Player attacker, Player victim){
        handleKnockback(direction,knockback,knockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    //Calculates direction based on hit + victim locations
    public static void handleInteraction(Location hitLocation, double damage, double knockback, Player attacker, Player victim){
        Vector direction = hitLocation.toVector().subtract(victim.getLocation().toVector());
        handleKnockback(direction,knockback,knockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }



    //Vertical = distinct y-kb
    public static void handleVerticalInteraction(double damage, double knockback, double yKnockback, Player attacker, Player victim){
        Vector direction = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        handleKnockback(direction,knockback,yKnockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleVerticalInteraction(Vector direction, double damage, double knockback, double yKnockback, Player attacker, Player victim){
        handleKnockback(direction,knockback,yKnockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleVerticalInteraction(Location hitLocation, double damage, double knockback, double yKnockback, Player attacker, Player victim){
        Vector direction = hitLocation.toVector().subtract(victim.getLocation().toVector());
        handleKnockback(direction,knockback,yKnockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }



    //Static = KB independent of health
    public static void handleStaticInteraction(double damage, double knockback, Player attacker, Player victim){
        Vector direction = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        handleKnockback(direction,knockback,knockback,true,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleStaticInteraction(Vector direction, double damage, double knockback, Player attacker, Player victim){
        handleKnockback(direction,knockback,knockback,true,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleStaticInteraction(Location hitLocation, double damage, double knockback, Player attacker, Player victim){
        Vector direction = hitLocation.toVector().subtract(victim.getLocation().toVector());
        handleKnockback(direction,knockback,knockback,true,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }



    //Private helpers
    private static void handleDamage(double damage, Player attacker, Player victim) {
        double currentHealth = victim.getHealth();
        damage *= dmgMitigation.getOrDefault(victim,1);
        victim.damage(1);
        if (currentHealth > 1.0) {
            victim.setHealth(Math.max(1.0, currentHealth - damage));
        }
    }

    private static void handleKnockback(Vector direction, double knockback, double yKnockback, boolean staticKB, Player victim){
        double[] xyzReduction = new double[]{0,0,0};
        knockback *= 0.16;
        yKnockback *= 0.8;
        if(!staticKB) {
            xyzReduction = reduceVelocity(victim);
            //Don't apply backwards kb
            for (double reduce : xyzReduction) if (reduce >= knockback) return;

            //reduce based on current velocity
            knockback -= xyzReduction[1];
            yKnockback -= xyzReduction[1];

            //increase based on victim's hp
            double missingHealth = victim.getMaxHealth() - victim.getHealth();
            knockback += missingHealth * knockback * 0.325;
            yKnockback += missingHealth * yKnockback * .325;
        }
        //Pitch???
        double weightMultiplier = 1 + (3 - HeroManager.getHero(victim).weight) / 5;
        //apply
    }

    private static void blood(Player victim) {
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

    //returns array with the magnitude of reduction for each component of velocity
    private static double[] reduceVelocity(Player victim){
        Vector check = victim.getVelocity();
        double x = check.getX();
        double y = check.getY();
        double z = check.getZ();
        double[] xyzReduction = new double[]{0,0,0};

        for (double i = -0.1; i > x; i -= 0.2) xyzReduction[0] += 0.05;
        for (double i = -0.4; i > y; i -= 0.2) xyzReduction[1] += 0.05;
        for (double i = -0.1; i > z; i -= 0.2) xyzReduction[2] += 0.05;

        return xyzReduction;
    }














    public static void handleInteractions(Vector vector, double knockback, double damage, Player attacker, Player victim) {
        handleInteractions(attacker.getLocation(), attacker.getLocation().clone().add(vector), knockback, damage, attacker, victim);
    }

    //public static void handleInteractions(Location location, double knockback, double damage, Player attacker, Player victim) {
    //    handleInteractions(victim.getLocation(), location, knockback, damage, attacker, victim);
    //}

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


        //calculates reduction based on current velocity
        //cancels kb if reduction > kb
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


        //calculates final kb based on hp
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

        //idk?
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
        Vector kb = target.getVelocity().multiply(0.8).add(direction.multiply(weight + finalKnockback * (1.0 - kbMitigation.getOrDefault(target,0))));
        kb.setY(kb.getY() - weight - finalVerticalKnockback * Math.sin(Math.toRadians(pitch)) * (1.0 - kbMitigation.getOrDefault(target,0)));
        return kb;
    }
}