package me.stolyy.heroes.characters.abilities;

import org.bukkit.entity.Player;

public interface Ability {
    String getName();
    long getCooldown(); // In milliseconds
    int getEnergyCost();
    
    // Returns true if ability was successfully used (triggering cooldown)
    boolean execute(Player player);
    
    // Optional: void onTick(Player player); for passive effects
}