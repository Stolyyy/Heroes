package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.HeroType;
import me.stolyy.heroes.heros.minions.BasherEntity;
import me.stolyy.heroes.heros.minions.PrincessEntity;
import me.stolyy.heroes.heros.abilities.Dash;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Pug extends HeroEnergy implements Dash {
    private final double weight = 2.5;
    private Cooldowns cooldowns;
    private double primaryDMG = 6;
    private double primaryRange = 9;
    private double primaryKB = 1;
    private double primaryCD = 2;
    private double secondaryDMG = 10;
    private double secondaryKB = 3.5;
    private double secondaryCD = 5;
    private int ultCD = 90;
    private double pounceCharge = 0;
    private double changePerTick = 0.35;

    private BasherEntity basher;
    private PrincessEntity princess;


    public Pug(Player player) {
        super(player);
        this.cooldowns = new Cooldowns(player, HeroType.MELEE, ultCD);
        initializeEnergy(changePerTick);
    }

    @Override
    public void usePrimaryAbility() {
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
    public void useSecondaryAbility() {
        if (cooldowns.isSecondaryReady()) {
            cooldowns.useSecondaryAbility(secondaryCD);
            Location center = player.getEyeLocation();
            Vector direction = player.getEyeLocation().getDirection();
            Set<Player> hitPlayers = new HashSet<>();
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 3.0f, 1.0f);

            new BukkitRunnable() {
                int tick = 0;
                final int totalTicks = 20;
                @Override
                public void run() {
                    if (tick >= totalTicks) {
                        this.cancel();
                        return;
                    }
                    // 6 rings
                    for (int ring = 0; ring < 6; ring++) {
                        double radius = 0.3 + (ring * 0.24); // radius 0.3 to 1.5
                        double distance = ring * 1.2; // Spacing of the rings
                        createRing(center, direction, radius, distance, ring);
                    }
                    tick++;
                }

                private void createRing(Location center, Vector direction, double radius, double distance, int ringIndex) {
                    int particles = 12; // 12 particles per ring
                    Vector up = new Vector(0, 1, 0);
                    Vector right = direction.getCrossProduct(up).normalize();
                    Vector planeNormal = direction.clone();

                    for (int i = 0; i < particles; i++) {
                        double angle = 2 * Math.PI * i / particles;
                        Vector offset = right.clone().multiply(radius * Math.cos(angle)).add(planeNormal.clone().crossProduct(right).multiply(radius * Math.sin(angle)));
                        Location particleLocation = center.clone().add(direction.clone().multiply(distance)).add(offset);
                        center.getWorld().spawnParticle(Particle.NOTE, particleLocation, 1, 0, 0, 0, 0);

                        // only hit players once
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
                    double knockbackStrength = abilityKB.get(AbilityType.SECONDARY) - (ringIndex * 0.5); // 3.5 to 1
                    Vector knockbackDirection = source.getEyeLocation().getDirection();
                    Interactions.handleInteractions(knockbackDirection, knockbackStrength, abilityDMG.get(AbilityType.SECONDARY), source, target
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 3L);
        }
    }


    @Override
    public void useUltimateAbility() {
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
    public void passiveAbility1() {
        if (getEnergy() >= 30) {
            removeEnergy(30);
            pounceCharge = 0;
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 2.0f, 1.0f);
            setCanIncreaseEnergy(false);
            new PounceChargeTask().runTaskTimer(Heroes.getInstance(), 0L, 1L);
        }
    }

    private void updatePrimaryDamage(double energyUsed) {
        primaryDMG = 6 + energyUsed / 70 * 4;
    }

    private class PounceChargeTask extends BukkitRunnable {
        @Override
        public void run() {
            if (player.isSneaking() && getEnergy() > 0) {
                pounceCharge += 1;
                removeEnergy(1);
                updateXpBar();
            } else {
                this.cancel();
                passiveAbility2();
                setCanIncreaseEnergy(true);
            }
        }
    }

    @Override
    public void passiveAbility2() {
        updatePrimaryDamage(pounceCharge);
        Vector direction = player.getEyeLocation().getDirection();
        player.setVelocity(direction.multiply(1 + (pounceCharge / 20)));
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            primaryDMG = 6;
        }, 20L);
    }


    @Override
    protected void stats() {

    }
}