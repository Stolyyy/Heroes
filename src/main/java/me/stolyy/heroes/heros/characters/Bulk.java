package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.WallDetection;
import me.stolyy.heroes.heros.AbilityType;
import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.heros.HeroType;
import me.stolyy.heroes.heros.commonabilities.Projectile;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Bulk extends Hero implements Listener, me.stolyy.heroes.heros.commonabilities.Projectile {
    public Player player;
    final double weight = 5;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.MELEE;
    }
    public double primaryKB = 0.9;
    public double primaryDMG = 8;
    public double primaryCD = 5;

    public Bulk(Player player) {
        this.player = player;
    }

    @Override
    public void usePrimaryAbility() {
        Projectile.projectile(player, 2, true, 1004, 2, this, AbilityType.PRIMARY);
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType){
        carContact(location);
        Interactions.handleInteractions(player.getLocation(), primaryKB, primaryDMG, player, target);
    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType){
        carContact(location);
    }

    public void carContact(Location location) {
        List<FallingBlock> fallingBlocks = new ArrayList<>();

        for (double t = 0.0; t < 17.0; t += 1.0) {
            Material mat = t < 3.0 ? Material.GLASS : (t < 6.0 ? Material.RED_SANDSTONE_SLAB : (t < 10.0 ? Material.RED_SANDSTONE : (t < 14.0 ? Material.COAL_BLOCK : (t < 16.0 ? Material.QUARTZ_BLOCK : Material.GLASS))));
            FallingBlock fb = location.getWorld().spawnFallingBlock(location.clone().add(0.0, 1.0, 0.0), mat.createBlockData());
            fb.setDropItem(false);

            Random r = new Random();
            double x = r.nextDouble() * 0.8 - 0.4;
            double y = r.nextDouble() * 0.8 - 0.4 + 0.1;
            double z = r.nextDouble() * 0.8 - 0.4;
            Vector velocity = new Vector(x, y, z);

            Location endLocation = fb.getLocation().add(velocity);
            if (!WallDetection.detectWall(fb.getLocation(), endLocation, 0.5)) {
                fb.setVelocity(velocity);
                fallingBlocks.add(fb);
            } else {
                fb.remove();
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (FallingBlock fb : fallingBlocks) {
                    if (fb != null && fb.isValid()) {
                        fb.remove();
                    }
                }
            }
        }.runTaskLater(Heroes.getInstance(), 200L);
    }

    @Override
    public void useSecondaryAbility() {
        // Implementation for secondary ability
    }

    @Override
    public void useUltimateAbility() {
        // Implementation for ultimate ability
    }

    @Override
    public void passiveAbility1() {
        // Implementation for passive ability 1
    }

    @Override
    public void passiveAbility2() {
        // Implementation for passive ability 2
    }
}