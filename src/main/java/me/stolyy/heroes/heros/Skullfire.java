package me.stolyy.heroes.heros;

import me.stolyy.heroes.Hero;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Interactions;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Skullfire extends Hero implements Hitscan, Projectile{
    Player player;
    final double weight = 2;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.RANGED;
    }
    AbilityListener jumpCap = new AbilityListener(Heroes.getInstance().getHeroManager(), Heroes.getInstance().getGameManager());
    boolean inPrimary = false;
    double primaryDMG = 3;
    double primaryKB = 0.3;
    double primaryCD = 1.5;
    int shotsPerClick = 1;
    double secondaryDMG = 7;
    double secondaryRange = 75;
    double secondaryKB = 0.8;
    double secondaryCD = 5;
    boolean inSecondary = false;
    boolean inUltimate = false;
    int ultTime = 10;
    Map<Player, Integer> chainCount = new HashMap<>();

    public Skullfire(Player player) {this.player=player;}

    @Override
    public void usePrimaryAbility(Player player) {
        if(inPrimary) {
            return;
        }
        inPrimary = true;
        Hero h = this;
        if (player.isSneaking()) {
            shotsPerClick = 3;
            primaryCD = 1;
        } else {
            shotsPerClick = 1;
            primaryCD = 0.5;
        }
        new BukkitRunnable() {
            private int count = 0;
            @Override
            public void run() {
                if (count >= shotsPerClick) {
                    this.cancel();
                    return;
                }
                if(inUltimate) {
                    Hitscan.hitscan(100, player.getEyeLocation(), player.getEyeLocation().getDirection(), Particle.SOUL_FIRE_FLAME, Color.WHITE, player, h, AbilityType.ULTIMATE);
                } else {
                    Hitscan.hitscan(100, player.getEyeLocation(), player.getEyeLocation().getDirection(), Particle.ASH, Color.WHITE, player, h, AbilityType.PRIMARY);
                }
                count++;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 4L);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inPrimary = false, (long) (primaryCD * 20L));
    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
        if(jumpCap.maxDoubleJumps.getOrDefault(player, 2) < 7) {
            jumpCap.maxDoubleJumps.put(player, jumpCap.maxDoubleJumps.getOrDefault(player, 2) + 1);
        }
        switch (abilityType) {
            case PRIMARY:
                Interactions.handleInteractions(player.getEyeLocation().getDirection(), primaryKB, primaryDMG, player, target);
                break;
            case ULTIMATE:
                Interactions.handleInteractions(player.getEyeLocation().getDirection(), primaryKB*1.2, primaryDMG+1, player, target);
                chainCount.put(target, chainCount.getOrDefault(target, 0)+1);
                chainUpdate(target);
                break;
        }

    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {
    }

    @Override
    public void useSecondaryAbility(Player player) {
        if(inSecondary) {
            return;
        }
        inSecondary = true;
        Projectile.projectile(player,2.5,true, Material.PUMPKIN, 0.5, this, AbilityType.SECONDARY);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inSecondary = false, (long) (secondaryCD * 20L));
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        grenadeContact(location);
    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {
        grenadeContact(location);
    }

    public void grenadeContact(Location location) {
        location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 1);
        List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(location, 4, 4, 4);
        for (Player nearbyPlayer : nearbyPlayers) {
            if (nearbyPlayer != player) {
                Interactions.handleInteractions(player.getLocation(), secondaryKB, secondaryDMG, player, nearbyPlayer);
                return;
            }
        }
    }

    @Override
    public void useUltimateAbility(Player player) {
        if (inUltimate) {
            return;
        }
        inUltimate = true;
        new UltTimer(player, ultTime).runTaskTimer(Heroes.getInstance(), 0L, 20L);
        new BukkitRunnable() {
            @Override
            public void run() {
                inUltimate = false;
            }
        }.runTaskLater(Heroes.getInstance(), ultTime * 20L);
    }

    public void chainUpdate(Player target) {
        double currentHealth = target.getHealth();
        if(chainCount.getOrDefault(target, 0) == 1) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 1));
            createParticleRing(target,1, 40);
        } else if(chainCount.getOrDefault(target, 0) == 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 2));
            createParticleRing(target,0.6, 40);
        }
        if(chainCount.getOrDefault(target, 0) == 3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 3));
            new BukkitRunnable(){
                int ticksRun = 0;
                @Override
                public void run(){
                    if (ticksRun >= 5) {
                        this.cancel();
                        return;
                    }
                    target.setVelocity(new Vector(0,0,0));
                    ticksRun++;
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
            createParticleRing(target,1.5, 20);
            if (currentHealth > 1.0) {
                target.setHealth(Math.max(1.0, currentHealth - 5));
            } else if (currentHealth <= 1.0) {
                target.damage(target.getHealth(), player);
            }
            chainCount.put(target,0);
        }
    }

    public void createParticleRing(Player player, double heightOffset, double duration) {
        final double radius = 1.0; // Radius of the ring
        final int particlesPerRing = 20; // Number of particles in each ring

        new BukkitRunnable() {
            int ticksRun = 0;

            @Override
            public void run() {
                if (ticksRun >= duration) {
                    this.cancel();
                    return;
                }

                Location playerLocation = player.getLocation().add(0, heightOffset, 0); // Add height offset here
                Vector upVector = new Vector(0, 1, 0);

                for (int i = 0; i < particlesPerRing; i++) {
                    double angle = 2 * Math.PI * i / particlesPerRing;
                    Vector offset = new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    Vector rotated = rotateAroundAxis(offset, upVector, playerLocation.getYaw());

                    Location particleLocation = playerLocation.clone().add(rotated);
                    player.getWorld().spawnParticle(Particle.FLAME, particleLocation, 1, 0, 0, 0, 0);
                }

                ticksRun++;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    private Vector rotateAroundAxis(Vector vector, Vector axis, double angle) {
        angle = Math.toRadians(angle);
        Vector parallel = axis.multiply(axis.dot(vector));
        Vector perpendicular = vector.subtract(parallel);
        Vector crossProduct = axis.crossProduct(perpendicular);
        return parallel.add(perpendicular.multiply(Math.cos(angle))).add(crossProduct.multiply(Math.sin(angle)));
    }

    @Override
    public void passiveAbility1(Player player) {
    }

    @Override
    public void passiveAbility2(Player player) {

    }




}
