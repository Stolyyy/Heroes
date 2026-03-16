package me.stolyy.heroes.characters.mechanics;

import me.stolyy.heroes.utility.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Set;

public class JabMechanic {

    private static final double JAB_RANGE = 3.5;
    private static final double JAB_DAMAGE = 2.0;

    public void performJab(Player attacker) {
        Location eyeLoc = attacker.getEyeLocation();
        
        // Use your existing Hitbox utility for consistent detection
        Set<Player> targets = Hitbox.cone(eyeLoc, 1.5, JAB_RANGE);
        targets.remove(attacker);

        for (Player target : targets) {
            // Apply damage and knockback
            target.damage(JAB_DAMAGE, attacker);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
            // Knockback logic would go here
        }
    }
}