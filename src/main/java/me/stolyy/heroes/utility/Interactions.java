package me.stolyy.heroes.utility;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameEnums;
import me.stolyy.heroes.game.minigame.GameListener;
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
        if(!canInteract(attacker, victim)) return;
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        handleKnockback(direction,knockback,0,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    //Calculates direction based on direction vector
    public static void handleInteraction(Vector direction, double damage, double knockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        handleKnockback(direction,knockback,0,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    //Calculates direction based on hit + victim locations
    public static void handleInteraction(Location hitLocation, double damage, double knockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        Vector direction = victim.getLocation().toVector().subtract(hitLocation.toVector());
        handleKnockback(direction,knockback,0,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }



    //Vertical = distinct y-kb
    public static void handleVerticalInteraction(double damage, double knockback, double yKnockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        handleKnockback(direction,knockback,yKnockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleVerticalInteraction(Vector direction, double damage, double knockback, double yKnockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        handleKnockback(direction,knockback,yKnockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleVerticalInteraction(Location hitLocation, double damage, double knockback, double yKnockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        Vector direction = victim.getLocation().toVector().subtract(hitLocation.toVector());
        handleKnockback(direction,knockback,yKnockback,false,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }



    //Static = KB independent of health
    public static void handleStaticInteraction(double damage, double knockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        handleKnockback(direction,knockback,0,true,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleStaticInteraction(Vector direction, double damage, double knockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        handleKnockback(direction,knockback,0,true,victim);
        blood(victim);
        handleDamage(damage, attacker, victim);
    }
    public static void handleStaticInteraction(Location hitLocation, double damage, double knockback, Player attacker, Player victim){
        if(!canInteract(attacker, victim)) return;
        Vector direction = victim.getLocation().toVector().subtract(hitLocation.toVector());
        handleKnockback(direction,knockback,0,true,victim);
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
        knockback *= 0.16;
        yKnockback *= 0.16;
        if(!staticKB) {
            double[] xyzReduction = reduceVelocity(victim);
            //Don't apply backwards kb
            for (double reduce : xyzReduction) if (reduce >= knockback) return;

            //reduce based on current velocity
            knockback -= xyzReduction[1];
            yKnockback -= xyzReduction[1];

            //increase based on victim's hp
            double missingHealth = victim.getMaxHealth() - victim.getHealth();
            knockback += missingHealth * knockback * 0.325;
            yKnockback += missingHealth * yKnockback * 0.325;
        }

        //ensure upwards kb if player is on ground
        /*
        double pitch = -Math.toDegrees(Math.asin(direction.getY()));
        if(victim.isOnGround()){
            pitch = Math.min(pitch, -10.0);
        }
        direction.setY(direction.getY() * (1 - Math.abs(pitch) / 90));
         */

        //check for nonfinite vectors
        if (!Double.isFinite(direction.getX()) || !Double.isFinite(direction.getY()) ||
                !Double.isFinite(direction.getZ()) || direction.lengthSquared() < 0.0001)
            direction = new Vector(0, 1, 0);


        double weightMultiplier = 1 + (3 - HeroManager.getHero(victim).weight) / 5;
        double mitigationMultiplier = kbMitigation.getOrDefault(victim,1);

        victim.setVelocity(new Vector(0,0,0));
        Vector kb = direction.normalize();
        kb.multiply(knockback*weightMultiplier*mitigationMultiplier);
        //kb.setY(kb.getY() + yKnockback*weightMultiplier*mitigationMultiplier);
        victim.setVelocity(kb);
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

    //check for gamemode adventure, same game, different teams, and if alive
    public static boolean canInteract(Player attacker, Player victim){
        if(!victim.getGameMode().equals(GameMode.ADVENTURE)) return false;

        Game attackerGame = GameManager.getPlayerGame(attacker);
        Game victimGame = GameManager.getPlayerGame(victim);
        if(attackerGame == null || victimGame == null) return false;
        if(!attackerGame.equals(victimGame)) return false;

        Map<Player, GameEnums.GameTeam> teams = attackerGame.getPlayerTeams();
        if(teams.get(attacker) == teams.get(victim)) return false;

        if(GameListener.isRespawning(victim)) return false;

        if(!victimGame.getAlivePlayerList().contains(victim)) return false;

        return true;
    }
}