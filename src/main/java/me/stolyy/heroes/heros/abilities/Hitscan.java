package me.stolyy.heroes.heros.abilities;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.utility.WallDetection;
import me.stolyy.heroes.heros.Hero;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public interface Hitscan {
    //Hitscan using particles
    static void hitscan(Player player, AbilityType abilityType, double radius, double range, Particle particle, Color color) {
        Location startLocation = player.getEyeLocation().clone();
        Vector direction = startLocation.getDirection().clone();
        Location endLocation = startLocation.clone().add(direction.clone().multiply(range));

        double distance = startLocation.distance(endLocation);
        Vector normalizedDirection = direction.clone().normalize();

        for (double i = 0; i <= distance; i += 0.1) {
            Location particleLocation = startLocation.clone().add(normalizedDirection.clone().multiply(i));
            if (particle == Particle.DUST) {
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1);
                player.getWorld().spawnParticle(Particle.DUST, particleLocation, 1, dustOptions);
            } else {
                player.getWorld().spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
            }

            List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(particleLocation, radius);
            for (Player nearbyPlayer : nearbyPlayers) {
                if (nearbyPlayer != player) {
                    ((Hitscan) HeroManager.getHero(player)).onHitscanHit(nearbyPlayer, particleLocation, abilityType);
                    return;
                }
            }
        }

        if (WallDetection.detectWall(startLocation, endLocation, 0.1)) {
            ((Hitscan) HeroManager.getHero(player)).onHitscanHitWall(endLocation, abilityType);
        }
    }

    //Hitscan using custom models
    static void hitscan(Player player, AbilityType abilityType, double radius, double range, int customModelData) {
        Location startLocation = player.getEyeLocation().clone();
        Vector direction = startLocation.getDirection().clone();
        Location endLocation = startLocation.clone().add(direction.clone().multiply(range));

        if (WallDetection.detectWall(startLocation, endLocation, 0.1)) {
            endLocation = getWallHitLocation(startLocation, endLocation, 0.1);
        }

        double distance = startLocation.distance(endLocation);
        Vector normalizedDirection = direction.clone().normalize();
        World world = player.getWorld();

        for (double i = 0; i <= distance; i += 0.6) {
            Location spawnLocation = startLocation.clone().add(normalizedDirection.clone().multiply(i)).subtract(0, 2, 0);

            ArmorStand armorStand = (ArmorStand) world.spawnEntity(spawnLocation, EntityType.ARMOR_STAND);
            armorStand.teleport(armorStand.getLocation().setDirection(direction));
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setMarker(true);
            ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                item.setItemMeta(meta);
            }
            armorStand.getEquipment().setHelmet(item);

            new BukkitRunnable() {
                @Override
                public void run() {
                    armorStand.remove();
                }
            }.runTaskLater(Heroes.getInstance(), 5L);

            List<Player> nearbyPlayers = (List<Player>) world.getNearbyPlayers(spawnLocation.clone().add(0, 2, 0), radius);
            for (Player nearbyPlayer : nearbyPlayers) {
                if (nearbyPlayer != player) {
                    ((Hitscan) HeroManager.getHero(player)).onHitscanHit(nearbyPlayer, spawnLocation.clone().add(0, 2, 0), abilityType);
                    return;
                }
            }
        }

        if (WallDetection.detectWall(startLocation, endLocation, 0.1)) {
            ((Hitscan) HeroManager.getHero(player)).onHitscanHitWall(endLocation, abilityType);
        }
    }

    private static Location getWallHitLocation(Location start, Location end, double step) {
        Vector direction = end.toVector().subtract(start.toVector()).normalize();
        double distance = start.distance(end);

        for (double d = 0; d <= distance; d += step) {
            Location checkLoc = start.clone().add(direction.multiply(d));
            if (WallDetection.detectWall(start, checkLoc, 0.1)) {
                return checkLoc;
            }
        }

        return end;
    }

    void onHitscanHit(Player target, Location location, AbilityType abilityType);
    void onHitscanHitWall(Location location, AbilityType abilityType);
}