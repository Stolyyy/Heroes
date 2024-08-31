package me.stolyy.heroes.Utility;

import me.stolyy.heroes.Games.GameManager;
import me.stolyy.heroes.HeroManager;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.Hero;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Interactions {
    //add hitbox detect in here

    public static void handleInteractions(Location sourceLocation, double knockback, double damage, Player damager, Player target) {
        handleInteractions(target.getLocation().subtract(sourceLocation).toVector().normalize(), knockback, damage, damager, target);
    }

    public static void handleInteractions(Vector direction, double knockback, double damage, Player damager, Player target) {
        GameManager gameManager = Heroes.getInstance().getGameManager();
        if(gameManager.getPlayerGame(damager) != gameManager.getPlayerGame(target)){
            return;
        } else if(gameManager.getPlayerGame(damager).arePlayersOnSameTeam(damager,target)){
            return;
        }
        handleKnockback(direction, knockback, target);
        handleDamage(damage, damager, target);
    }

    private static void handleKnockback(Vector direction, double knockback, Player target) {
        HeroManager heroManager = new HeroManager();
        Hero targetHero = heroManager.getHero(target);
        double targetWeight = (targetHero != null) ? targetHero.getWeight() : 3.0;
        double targetHealthPercentage = target.getHealth() / target.getMaxHealth();

        double finalKnockback = calculateKnockback(knockback, targetWeight, targetHealthPercentage);

        Vector knockbackVector = direction.multiply(finalKnockback);
        target.setVelocity(target.getVelocity().add(knockbackVector));
    }

    private static double calculateKnockback(double baseKnockback, double targetWeight, double healthPercentage) {
        double weightFactor = 7.50 / targetWeight;
        double healthFactor = 1 + Math.pow((1 - healthPercentage), 3);
        return baseKnockback * weightFactor * healthFactor;
    }

    private static void handleDamage(double damage, Player damager, Player target) {
        double currentHealth = target.getHealth();
        target.damage(1);
        if (currentHealth > 1.0) {
            target.setHealth(Math.max(1.0, currentHealth - damage));
        }
    }
}