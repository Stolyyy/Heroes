package me.stolyy.heroes.utility;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameEnums;
import me.stolyy.heroes.game.minigame.GameListener;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.heros.characters.Bug;
import me.stolyy.heroes.utility.effects.Particles;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;

//Map: player, player who hit them last (for stats)
public class Interactions {
    //Knockback direction is calculated based on one of the following:
    //1. Attacker + Victim locations
    //2. Direction vector
    //3. Hit location + Victim locations

    //Normal knockback
    public static void handleInteraction(double damage, double knockback, Player attacker, Player victim){
        handleInteraction(victim.getLocation().toVector().subtract(attacker.getLocation().toVector()), damage, knockback, 0, false, attacker, victim);
    }
    public static void handleInteraction(Vector direction, double damage, double knockback, Player attacker, Player victim){
        handleInteraction(direction, damage, knockback, 0, false, attacker, victim);
    }
    public static void handleInteraction(Location hitLocation, double damage, double knockback, Player attacker, Player victim){
        handleInteraction(victim.getLocation().toVector().subtract(hitLocation.toVector()), damage, knockback, 0, false, attacker, victim);
    }

    //Vertical = distinct y-kb
    public static void handleVerticalInteraction(double damage, double knockback, double yKnockback, Player attacker, Player victim){
        handleInteraction(victim.getLocation().toVector().subtract(attacker.getLocation().toVector()), damage, knockback, yKnockback, false, attacker, victim);
    }
    public static void handleVerticalInteraction(Vector direction, double damage, double knockback, double yKnockback, Player attacker, Player victim){
        handleInteraction(direction, damage, knockback, yKnockback, false, attacker, victim);
    }
    public static void handleVerticalInteraction(Location hitLocation, double damage, double knockback, double yKnockback, Player attacker, Player victim){
        handleInteraction(victim.getLocation().toVector().subtract(hitLocation.toVector()), damage, knockback, yKnockback, false, attacker, victim);
    }

    //Static = KB independent of health
    public static void handleStaticInteraction(double damage, double knockback, Player attacker, Player victim){
        handleInteraction(victim.getLocation().toVector().subtract(attacker.getLocation().toVector()), damage, knockback, 0, true, attacker, victim);
    }
    public static void handleStaticInteraction(Vector direction, double damage, double knockback, Player attacker, Player victim){
        handleInteraction(direction, damage, knockback, 0, true, attacker, victim);
    }
    public static void handleStaticInteraction(Location hitLocation, double damage, double knockback, Player attacker, Player victim){
        handleInteraction(victim.getLocation().toVector().subtract(hitLocation.toVector()), damage, knockback, 0, true, attacker, victim);
    }





    //Private helpers
    private static void handleInteraction(Vector direction, double damage, double knockback, double yKnockback, boolean staticKB, Player attacker, Player victim) {
        if(!canInteract(attacker, victim)) return;
        handleKnockback(direction.clone().normalize(), knockback, yKnockback, staticKB, victim);
        Particles.blood(victim);
        handleDamage(damage, attacker, victim);
    }


    private static void handleDamage(double damage, Player attacker, Player victim) {
        double currentHealth = victim.getHealth();
        damage *= HeroManager.getHero(victim).damageMultiplier();
        victim.damage(1);
        if (currentHealth > 1.0) {
            victim.setHealth(Math.max(1.0, currentHealth - damage));
        }

        if(HeroManager.getHero(victim) instanceof Bug bug){
            bug.onHit();
        }
    }


    private static void handleKnockback(Vector direction, double knockback, double yKnockback, boolean staticKB, Player victim){
        knockback *= 0.16;
        yKnockback *= 0.16;
        if(!staticKB) {
            double[] xyzReduction = reduceVelocity(victim);
            //Don't apply backwards kb
            for (double reduce : xyzReduction) if (reduce >= knockback) return;

            //reduce based on current velocity
            knockback -= xyzReduction[1];

            //increase based on victim's hp
            double missingHealth = victim.getMaxHealth() - victim.getHealth();
            knockback += missingHealth * knockback * 0.0625;
            yKnockback += missingHealth * yKnockback * 0.0625;
        } else {
            double staticMultiplier = 50; //static kb as if player is missing 50 health
            knockback += staticMultiplier * knockback * 0.0625;
            yKnockback += staticMultiplier * yKnockback * 0.0625;
        }

        //ensure upwards kb if on ground
        if(victim.isOnGround() && yKnockback >= 0){
            direction.setY(Math.max(direction.getY(), 0.2));
        } //potentially remove, but meant to ensure kb is not too extreme up/down.
        else if(direction.getY() < -0.2 && direction.getY() > -0.9){
            direction.setY(direction.getY() + 0.1);
        } else if(direction.getY() > 0.2 && direction.getY() < 0.9){
            direction.setY(direction.getY() - 0.1);
        }

        if (!Double.isFinite(direction.getX()) || !Double.isFinite(direction.getY()) ||
                !Double.isFinite(direction.getZ()) || direction.lengthSquared() < 0.0001)
            direction = new Vector(0, 1, 0);


        double weightMultiplier = 1 + (3 - HeroManager.getHero(victim).weight()) / 5;
        double mitigationMultiplier = HeroManager.getHero(victim).knockbackMultiplier();

        victim.setVelocity(new Vector(0,0,0));
        Vector kb = direction.normalize();
        kb.multiply(knockback*weightMultiplier*mitigationMultiplier);
        kb.setY(kb.getY() + yKnockback*weightMultiplier*mitigationMultiplier);
        victim.setVelocity(kb);
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


    //check for gamemode adventure, same game, different teams, and if alive
    public static boolean canInteract(Player attacker, Player victim){
        if(!victim.getGameMode().equals(GameMode.ADVENTURE)) return false;
        if(attacker.getUniqueId().equals(victim.getUniqueId())) return false;

        Game attackerGame = GameManager.getPlayerGame(attacker);
        Game victimGame = GameManager.getPlayerGame(victim);
        if(attackerGame == null || victimGame == null) return false;
        if(!attackerGame.equals(victimGame)) return false;

        if(!victimGame.onlinePlayers(false).contains(victim)) return false;

        if(GameListener.isRespawning(victim)) return false;

        if(attackerGame.sameTeam(attacker, victim)) return attackerGame.friendlyFire(attacker);

        return true;
    }
}