package me.stolyy.heroes.heros.abilities.interfaces;

import me.stolyy.heroes.heros.abilities.data.ShockwaveData;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.utility.effects.Particles;
import me.stolyy.heroes.utility.physics.Hitbox;
import me.stolyy.heroes.utility.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedHashSet;
import java.util.Set;

public interface Shockwave {
    default void shockwave(Player player, AbilityType abilityType, ShockwaveData shockwaveData) {
        Location l = player.getLocation().clone();
        l.setDirection(new Vector(0,1,0));
        Set<Player> hitPlayers = new LinkedHashSet<>(Hitbox.cylinder(l, shockwaveData.radius(), 3));
        hitPlayers.remove(player);

        if(!shockwaveData.piercesWalls()){
            hitPlayers.removeIf(p -> WallDetection.rayCast(player.getEyeLocation(), p.getLocation()));
        }

        if(shockwaveData.particle() != null) {
            //TODO: Particle explosion effect
            if(shockwaveData.dustOptions() != null) {
                Particles.directionalRing(l.add(0,0.25,0), shockwaveData.radius(), shockwaveData.particle(), shockwaveData.dustOptions());
            } else {
                Particles.directionalRing(l.add(0,0.25,0), shockwaveData.radius(), shockwaveData.particle());
            }
        }
        if(shockwaveData.usesBlocks()) {
            //shockwave blocks
        }

        for (Player target : hitPlayers) {
            onShockwaveHit(target, abilityType);
        }
    }

    void onShockwaveHit(Player target, AbilityType abilityType);
}
