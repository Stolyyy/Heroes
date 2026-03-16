package me.stolyy.heroes.characters.mechanics;

import me.stolyy.heroes.characters.Hero;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class DoubleJumpMechanic {

    public void performJump(Player player, Hero hero) {
        // 1. Get stats from Hero (you might need to add these getters to your Hero class)
        // double forwardStrength = hero.getDoubleJumpForwardStrength();
        // double upwardStrength = hero.getDoubleJumpUpwardStrength();
        
        // Placeholder values until Hero class is updated
        double forwardStrength = 1.2;
        double upwardStrength = 1.0;

        // 2. Calculate Velocity
        Vector direction = player.getLocation().getDirection().multiply(forwardStrength);
        direction.setY(upwardStrength);
        
        player.setVelocity(direction);
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f);
    }
}