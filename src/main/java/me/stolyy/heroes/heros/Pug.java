package me.stolyy.heroes.heros;

import me.stolyy.heroes.Hero;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Interactions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Pug extends Hero implements Dash {
    Player player;
    final double weight = 2.5;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.MELEE;
    }
    boolean inPrimary = false;
    double primaryDMG = 6;
    double primaryRange = 9;
    double primaryKB = 0.5;
    double primaryCD = 2;
    double secondaryDMG = 10;
    double secondaryRange = 18;
    double secondaryKB = 0.8;
    double secondaryCD = 5;
    boolean inSecondary = false;
    double ultCD = 90;
    boolean inUltimate = false;
    double pounceStrength = 0;
    double pounceCharge = 0;
    double energy = 100;
    double changePerTick = 0.25;
    boolean increaseEnergy = true;

    private BasherEntity basher;
    private PrincessEntity princess;


    public Pug(Player player) {
        this.player = player;
    }


    @Override
    public void usePrimaryAbility(Player player) {
        if(inPrimary) {
            return;
        }
        inPrimary = true;
        Dash.onDash(player, this, AbilityType.PRIMARY, primaryRange);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inPrimary = false, (long) (primaryCD * 20L));
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType){
        Interactions.handleInteractions(player.getEyeLocation().getDirection(), primaryKB, primaryDMG, player, target);
    }

    @Override
    public void useSecondaryAbility(Player player) {
        if(inSecondary) {
            return;
        }
        inSecondary = true;
        Location center = player.getEyeLocation();
        Vector direction = player.getEyeLocation().getDirection();
        Set<Player> hitPlayers = new HashSet<>();

        new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 20; // Duration of the ability

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    this.cancel();
                    return;
                }

                for (int ring = 0; ring < 6; ring++) {
                    double radius = 0.3 + (ring * 0.24); // 0.3 to 1.5 in 6 steps
                    double distance = ring * 1.2; // Increased spacing (0.72 * 5 = 3.6 blocks total length)
                    createRing(center, direction, radius, distance, ring);
                }

                tick++;
            }

            private void createRing(Location center, Vector direction, double radius, double distance, int ringIndex) {
                int particles = 12; // Reduced number of particles per ring
                Vector up = new Vector(0, 1, 0);
                Vector right = direction.getCrossProduct(up).normalize();
                Vector planeNormal = direction.clone();

                for (int i = 0; i < particles; i++) {
                    double angle = 2 * Math.PI * i / particles;
                    Vector offset = right.clone().multiply(radius * Math.cos(angle))
                            .add(planeNormal.clone().crossProduct(right).multiply(radius * Math.sin(angle)));
                    Location particleLocation = center.clone().add(direction.clone().multiply(distance)).add(offset);

                    center.getWorld().spawnParticle(Particle.NOTE, particleLocation, 1, 0, 0, 0, 0);

                    // Check for players hit by particles

                    List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(particleLocation, 1);
                    for (Player nearbyPlayer : nearbyPlayers) {
                        if (nearbyPlayer != player && !hitPlayers.contains(nearbyPlayer)) {
                            hitPlayer(nearbyPlayer, player, ringIndex);
                            hitPlayers.add(nearbyPlayer);
                        }
                    }
                }
            }

            private void hitPlayer(Player target, Player source, int ringIndex) {
                // Calculate knockback based on ring index (0 is closest, 5 is farthest)
                double knockbackStrength = 1.5 - (ringIndex * 0.2); // 1.5 to 0.5
                Vector knockbackDirection = source.getEyeLocation().getDirection();
                Interactions.handleInteractions(knockbackDirection, knockbackStrength, secondaryDMG, source, target
                );
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inSecondary = false, (long) (secondaryCD * 20L));
    }


    @Override
    public void useUltimateAbility(Player player) {
        if (inUltimate) {
            return;
        }
        inUltimate = true;
        new UltTimer(player, 13).runTaskTimer(Heroes.getInstance(), 0L, 20L);
        Location spawnLocation = player.getLocation();
        this.basher = new BasherEntity(Heroes.getInstance(), spawnLocation, player);
        this.princess = new PrincessEntity(Heroes.getInstance(), spawnLocation, player);

        // Set up timer to remove mini pugs after 13 seconds
        new UltimateEndTask().runTaskLater(Heroes.getInstance(), 13 * 20); // 13 seconds * 20 ticks/second
    }

    private class UltimateEndTask extends BukkitRunnable {
        @Override
        public void run() {
            removeMinipugs();
            inUltimate = false;
        }
    }

    private void removeMinipugs() {
        if (basher != null) {
            basher.remove();
            basher = null;
        }
        if (princess != null) {
            princess.remove();
            princess = null;
        }
    }

    @Override
    public void passiveAbility1(Player player) {
        if (energy >= 30) {
            energy -= 30;
            pounceCharge = 0;
            new PounceChargeTask().runTaskTimer(Heroes.getInstance(), 0L, 1L);
        }
    }

    private class PounceChargeTask extends BukkitRunnable {
        @Override
        public void run() {
            if (player.isSneaking() && energy > 0) {
                increaseEnergy = false;
                energy -= 1;
                pounceCharge += 1;
                updateXpBar();
            } else {
                this.cancel();
                passiveAbility2(player);
                increaseEnergy = true;
            }
        }
    }

    @Override
    public void passiveAbility2(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        player.setVelocity(direction.multiply(1.2 + (pounceCharge / 20)));
    }

    void updateEnergy() {
        new EnergyUpdateTask().runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    private class EnergyUpdateTask extends BukkitRunnable {
        @Override
        public void run() {
            if (energy < 100 && increaseEnergy) {
                energy += changePerTick;
            }
            if (energy > 100) {
                energy = 100; // Ensure energy does not exceed 100
            }
            updateXpBar();
        }
    }

    private void updateXpBar() {
        int xpLevels = (int) energy;
        float xpProgress = (float) (energy / 100.0f);
        xpProgress = Math.max(0.0f, Math.min(1.0f, xpProgress));
        player.setLevel(xpLevels);
        player.setExp(xpProgress);
    }
}