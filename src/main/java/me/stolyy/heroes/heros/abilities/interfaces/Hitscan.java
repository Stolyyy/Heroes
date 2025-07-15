package me.stolyy.heroes.heros.abilities.interfaces;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.config.AbilityType;
import me.stolyy.heroes.heros.abilities.data.HitscanData;
import me.stolyy.heroes.utility.effects.Particles;
import me.stolyy.heroes.utility.physics.Hitbox;
import me.stolyy.heroes.utility.physics.WallDetection;
import me.stolyy.heroes.utility.effects.ArmorStands;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedHashSet;
import java.util.Set;

public interface Hitscan {
    default void hitscan(Player player, AbilityType abilityType, HitscanData hitscanData) {
        boolean useParticles = hitscanData.particle() != null;

        Location startLocation = player.getEyeLocation();
        Vector direction = startLocation.getDirection();
        Location endLocation = startLocation.clone().add(direction.clone().multiply(hitscanData.range()));

        double distance = startLocation.distance(endLocation);
        Vector normalizedDirection = direction.clone().normalize();

        Set<Player> hitPlayers = new LinkedHashSet<>();
        Location currentLocation = startLocation.clone();

        for (double i = 0; i <= distance; i += 0.1) {
            currentLocation = currentLocation.add(normalizedDirection.clone().multiply(0.1));

            hitPlayers.addAll(Hitbox.sphere(currentLocation, hitscanData.radius()));
            hitPlayers.remove(player);

            if(!hitscanData.piercesPlayers() && !hitPlayers.isEmpty()) {
                break;
            }

            if(!hitscanData.piercesWalls() && WallDetection.rayCast(currentLocation, 0.1)) {
                onHitscanHitWall(currentLocation, abilityType);
                break;
            }

            if (useParticles) {
                Particles.summonParticle(currentLocation, hitscanData.particle(), hitscanData.dustOptions());
            }
            if(hitscanData.customModelData() > 0) {
                ArmorStand as = ArmorStands.summonArmorStand(currentLocation.clone().setDirection(direction), hitscanData.customModelData());
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), as::remove, 5L);
            }
        }

        for (Player hitPlayer : hitPlayers) {
            onHitscanHit(hitPlayer, currentLocation, abilityType);
        }
    }

    void onHitscanHit(Player target, Location location, AbilityType abilityType);
    default void onHitscanHitWall(Location location, AbilityType abilityType) {}
}