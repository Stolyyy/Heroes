package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.heros.abilities.Ability;
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
    private BasherEntity basher;
    private PrincessEntity princess;

    private double pounceCharge;
    private double primaryRange;

    public Pug(Player player){
        super(player);
    }

    @Override
    public void usePrimaryAbility() {
        if(!primary.ready) return;
        Dash.dash(player, AbilityType.PRIMARY, primaryRange);
        cooldown(primary);
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType){
        Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg + pounceCharge / 40, primary.kb, player, target);
        player.playSound(player.getLocation(), "melee.punch.meleehit", SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void useSecondaryAbility() {
        if(!secondary.ready) return;
        cooldown(secondary);
        Location center = player.getEyeLocation();
        Vector direction = player.getEyeLocation().getDirection();
        Set<Player> hitPlayers = new HashSet<>();
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 3.0f, 1.0f);

        new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 8;
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
                double knockbackStrength = secondary.kb - (ringIndex * 0.5); // 3.5 to 1
                Vector knockbackDirection = source.getEyeLocation().getDirection();
                Interactions.handleInteraction(knockbackDirection, secondary.dmg, knockbackStrength, source, target);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 3L);
    }

    @Override
    public void useUltimateAbility() {
        if(ultimate.inUse || !ultimate.ready) {
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + (int) ultimate.timeUntilUse + " seconds remaining.");
            return;
        }
        ultimate.inUse = true;

        player.playSound(player.getLocation(), "pug.intruders.activation", SoundCategory.MASTER, 5f, 1.0f);
        ultTimer();
        Location spawnLocation = player.getLocation();
        this.basher = new BasherEntity(Heroes.getInstance(), spawnLocation, player);
        this.princess = new PrincessEntity(Heroes.getInstance(), spawnLocation, player);

        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            removeMinipugs();
            cooldown(ultimate);
        },  (long) (ultimate.duration * 20L));
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
        if (energy >= 30) {
            energy -= 30;
            pounceCharge = 0;
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 2.0f, 1.0f);
            canIncreaseEnergy = false;
            new PounceChargeTask().runTaskTimer(Heroes.getInstance(), 0L, 1L);
        }
    }

    private class PounceChargeTask extends BukkitRunnable {
        @Override
        public void run() {
            if (player.isSneaking() && energy > 0) {
                pounceCharge += 1;
                energy -= 1;
                updateXpBar();
            } else {
                this.cancel();
                passiveAbility2();
                canIncreaseEnergy = true;
            }
        }
    }

    @Override
    public void passiveAbility2() {
        Vector direction = player.getEyeLocation().getDirection();
        player.setVelocity(direction.multiply(1 + (pounceCharge / 20)));
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            pounceCharge = 0;
        }, 20L);
    }

    @Override
    protected void stats() {
        heroType = HeroType.MELEE;
        weight = 2.5;

        primary = new Ability(AbilityType.PRIMARY,  6, 2.5, 2);
        secondary = new Ability(AbilityType.SECONDARY, 10, 5, 5.5);
        ultimate = new Ability(AbilityType.ULTIMATE, 0,0,90, 10);
        primaryRange = 9;
        pounceCharge = 0;

        setEnergyStats(100,100,0.75,true);
        initializeEnergyUpdates();
    }
}