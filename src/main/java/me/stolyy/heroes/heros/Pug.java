package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Utility.Interactions;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Pug extends Hero implements Dash, Energy {
    Player player;
    final double weight = 2.5;
    private Cooldowns cooldowns;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.MELEE;
    }
    double primaryDMG = 6;
    double primaryRange = 9;
    double primaryKB = 1;
    double primaryCD = 2;
    double secondaryDMG = 10;
    double secondaryKB = 3.5;
    double secondaryCD = 5;
    int ultCD = 90;
    double pounceCharge = 0;
    double energy = 100;
    double changePerTick = 0.35;
    boolean increaseEnergy = true;

    private BasherEntity basher;
    private PrincessEntity princess;


    public Pug(Player player) {
        this.player = player;
        this.cooldowns = new Cooldowns(player, HeroType.MELEE, ultCD);
        initializeEnergy(player, changePerTick);
    }


    @Override
    public void usePrimaryAbility(Player player) {
        if(cooldowns.isPrimaryReady()) {
            Dash.onDash(player, this, AbilityType.PRIMARY, primaryRange);
            cooldowns.usePrimaryAbility(primaryCD);
        }
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType){
        Interactions.handleInteractions(player.getEyeLocation().getDirection(), primaryKB, primaryDMG, player, target);
        player.playSound(player.getLocation(), "melee.punch.meleehit", SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void useSecondaryAbility(Player player) {
        if (cooldowns.isSecondaryReady()) {
            cooldowns.useSecondaryAbility(secondaryCD);
            Location center = player.getEyeLocation();
            Vector direction = player.getEyeLocation().getDirection();
            Set<Player> hitPlayers = new HashSet<>();
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 3.0f, 1.0f);
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
                    double knockbackStrength = secondaryKB - (ringIndex * 0.5); // 3.5 - 1
                    Vector knockbackDirection = source.getEyeLocation().getDirection();
                    Interactions.handleInteractions(knockbackDirection, knockbackStrength, secondaryDMG, source, target
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 3L);
        }
    }


    @Override
    public void useUltimateAbility(Player player) {
        if (cooldowns.getUltimateCooldown() == 0) {
            cooldowns.useUltimateAbility();
            player.playSound(player.getLocation(), "pug.intruders.activation", SoundCategory.MASTER, 5f, 1.0f);
            new UltTimer(player, 13).runTaskTimer(Heroes.getInstance(), 0L, 20L);
            Location spawnLocation = player.getLocation();
            this.basher = new BasherEntity(Heroes.getInstance(), spawnLocation, player);
            this.princess = new PrincessEntity(Heroes.getInstance(), spawnLocation, player);

            // Set up timer to remove mini pugs after 13 seconds
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> removeMinipugs(),  (13 * 20L));
        } else {
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + cooldowns.getUltimateCooldown() + " seconds remaining.");
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
        if (getEnergy(player) >= 30) {
            removeEnergy(player, 30);
            pounceCharge = 0;
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 2.0f, 1.0f);
            setCanIncreaseEnergy(player, false);
            new PounceChargeTask().runTaskTimer(Heroes.getInstance(), 0L, 1L);
        }
    }

    private void updatePrimaryDamage(double energyUsed) {
        primaryDMG = 6 + energyUsed / 70 * 4;
    }

    private class PounceChargeTask extends BukkitRunnable {
        @Override
        public void run() {
            if (player.isSneaking() && getEnergy(player) > 0) {
                pounceCharge += 1;
                removeEnergy(player,1);
                updateXpBar(player);
            } else {
                this.cancel();
                passiveAbility2(player);
                setCanIncreaseEnergy(player, true);
            }
        }
    }

    @Override
    public void passiveAbility2(Player player) {
        updatePrimaryDamage(pounceCharge);
        Vector direction = player.getEyeLocation().getDirection();
        player.setVelocity(direction.multiply(1 + (pounceCharge / 20)));
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            primaryDMG = 6;
        }, 20L);
    }
}