package me.stolyy.heroes.heros.abilities.interfaces;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.abilities.data.ConeData;
import me.stolyy.heroes.utility.effects.ArmorStands;
import me.stolyy.heroes.utility.effects.Particles;
import me.stolyy.heroes.utility.physics.Hitbox;
import me.stolyy.heroes.utility.physics.WallDetection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;

public interface Cone {
    default void cone(Player player, AbilityType abilityType, ConeData coneData) {
        Location l = player.getEyeLocation().clone();
        Set<Player> hitPlayers = new LinkedHashSet<>(Hitbox.cone(l, coneData.radius(), coneData.length()));
        hitPlayers.remove(player);
        if(!coneData.piercesWalls()){
            hitPlayers.removeIf(p -> WallDetection.rayCast(player.getEyeLocation(), p.getLocation()));
        }

        if(coneData.particle() != null) {
            for(int i = 0; i < coneData.length(); i++) {
                //TODO: More explodey idk
                if(coneData.dustOptions() != null) {
                    Particles.directionalRing(l.add(l.toVector().normalize().multiply(i)), coneData.radius() * i / coneData.length(), coneData.particle(), coneData.dustOptions());
                } else {
                    Particles.directionalRing(l.add(l.toVector().normalize().multiply(i)), coneData.radius() * i / coneData.length(), coneData.particle());
                }
            }
        } else {
            ArmorStand as = ArmorStands.summonArmorStand(l.add(l.toVector().normalize()), coneData.customModelData());
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), as::remove, 5L);
        }

        for (Player target : hitPlayers) {
            onConeHit(target, abilityType);
        }
    }

    void onConeHit(Player target, AbilityType abilityType);
}
