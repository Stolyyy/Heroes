package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.heros.HeroType;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.WallDetection;
import me.stolyy.heroes.heros.abilities.Hitscan;
import me.stolyy.heroes.heros.abilities.Projectile;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Shoop extends HeroEnergy implements Projectile, Hitscan, Listener {
    private double primarySpeed;
    private Color secondaryColor;
    private double secondaryRange;
    private Set<Player> ultimateHits;
    private Ability passive;
    private double passiveSpeed;

    public Shoop(Player player) {
        super(player);
    }



    @Override
    public void usePrimaryAbility() {
        if(!primary.ready) return;
        Projectile.projectile(player, AbilityType.PRIMARY, primarySpeed,1, false, 14003);
        cooldown(primary);
        player.playSound(player.getLocation(), "shoopdawhoop.active", SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        if (secondary.cd < 5) {
            secondary.cd++;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage("Secondary Charge: " + secondary.cd);
        switch (abilityType) {
            case PRIMARY:
                Interactions.handleInteraction(primary.dmg, primary.kb, player, target);
                break;
            case PASSIVE:
                Interactions.handleInteraction(passive.dmg, passive.kb, player, target);
                break;
        }
        onRangedHit();
    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {
    }



    @Override
    public void useSecondaryAbility() {
        Location targetLocation = player.getEyeLocation().clone();
        Vector direction = targetLocation.getDirection();
        if (secondary.cd == 0) return;
        else if (secondary.cd == 1) {
            secondary.dmg = 2;
            secondary.kb = 1;
            secondaryColor = Color.YELLOW;
        } else if (secondary.cd == 2) {
            secondary.dmg = 4;
            secondary.kb = 2;
            secondaryColor = Color.LIME;
        } else if (secondary.cd == 3) {
            secondary.dmg = 7;
            secondary.kb = 3;
            secondaryColor = Color.BLUE;
        } else if (secondary.cd == 4) {
            secondary.dmg = 10;
            secondary.kb = 4;
            secondaryColor = Color.PURPLE;
        } else if (secondary.cd == 5) {
            secondary.cd = 0;
            secondary.dmg = 15;
            secondary.kb = 5;
            //maybe use hitscan call and then use particles around it, if statement after default calls
            for (double i = 0; i < secondaryRange; i += 0.1) {
                Location nextLocation = targetLocation.clone().add(direction.clone().multiply(0.1));
                if (WallDetection.detectWall(targetLocation, nextLocation, 0.5)) {
                    break;
                }
                targetLocation = nextLocation;
                player.playSound(player.getLocation(), "shoopdawhoop.chargedbeam.big", SoundCategory.MASTER, 2.5f, 1.0f);
                int numParticles = 16;
                for (int j = 0; j < numParticles; j++) {
                    double angle = 2 * Math.PI * j / numParticles;
                    double x = 0.3 * Math.cos(angle);
                    double z = 0.3 * Math.sin(angle);
                    Location particleLocation = targetLocation.clone().add(x, 0, z);
                    Particle.DustOptions dustOptions = new Particle.DustOptions(j % 2 == 0 ? Color.RED : Color.WHITE, 1);
                    player.getWorld().spawnParticle(Particle.DUST, particleLocation, 1, dustOptions);
                }
                List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(targetLocation, 1);
                for (Player nearbyPlayer : nearbyPlayers) {
                    if (nearbyPlayer != player) {
                        Interactions.handleInteraction(player.getEyeLocation().getDirection(), secondary.dmg, secondary.kb, player, nearbyPlayer);
                        return;
                    }
                }
            }
            return;
        }
        secondary.cd = 0;
        Hitscan.hitscan(player,AbilityType.SECONDARY,1,secondaryRange,Particle.DUST, secondaryColor);
        player.playSound(player.getLocation(), "shoopdawhoop.chargedbeam.small", SoundCategory.MASTER, 2.0f, 1.0f);
    }


    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        switch (abilityType) {
            case SECONDARY -> {
                Interactions.handleInteraction(player.getLocation().getDirection(), secondary.dmg, secondary.kb, player, target);
                onRangedHit();
            }
            case ULTIMATE -> {
                if(ultimateHits.contains(target)) return;
                ultimateHits.add(target);
                Interactions.handleInteraction(player.getLocation().getDirection(), ultimate.dmg, ultimate.kb, player, target);
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> ultimateHits.remove(target), 10L);
            }
        }
    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {
    }



    @Override
    public void useUltimateAbility() {
        if(!ultimate.ready || ultimate.inUse) {
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + (int) ultimate.timeUntilUse + " seconds remaining.");
        }
        ultTimer();
        ultimate.inUse = true;
        player.playSound(player.getLocation(), "shoopdawhoop.crystal", SoundCategory.MASTER, 5.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.0f), (20L));
        new BukkitRunnable(){
            private int timer = 0;

            @Override
            public void run(){
                timer++;
                if(timer >= ultimate.duration * 20 / 3){
                    this.cancel();
                    return;
                }
                Hitscan.hitscan(player,AbilityType.ULTIMATE,1,secondaryRange,14004);
            }
        }.runTaskTimer(Heroes.getInstance(), 20L, 3L);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> cooldown(ultimate), 4L);
    }



    @Override
    public void passiveAbility1() {
        //can't use twice because u can't have >55 energy if ur on it already
        if(energy < 55 || passive.inUse) return;
        energy -= 55;
        canIncreaseEnergy = false;

        ArmorStand armorStand = Projectile.projectile(player,AbilityType.PASSIVE, passiveSpeed, 1, false, 14003);
        armorStand.addPassenger(player);

        player.playSound(player.getLocation(), "shoopdawhoop.active", SoundCategory.MASTER, 3.0f, 1.0f);
        passive.inUse = true;

        //prevent player from immediately getting off
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> passive.inUse = false, 10L);

        new BukkitRunnable(){
            Location lastLocation = armorStand.getLocation();

            @Override
            public void run() {
                if(armorStand.isDead() || !armorStand.isValid() || energy <= 0 || !player.isInsideVehicle()) {
                    player.leaveVehicle();
                    canIncreaseEnergy = true;
                    this.cancel();
                    return;
                }
                Location currentLocation = armorStand.getLocation();
                double distance = lastLocation.distance(currentLocation);
                energy = Math.max(energy - distance,0);
                lastLocation = currentLocation;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 2L);
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() == player) {
            //prevent player from accidentally dismounting when activating
            if (passive.inUse && energy > 10) {
                event.setCancelled(true);
            }
            Location newLocation = player.getLocation().add(0, 0.75, 0);
            player.teleport(newLocation);
        }
    }

    @Override
    public void passiveAbility2(){
    }



    @Override
    protected void stats() {
        heroType = HeroType.RANGED;
        weight = 3;

        primary = new Ability(AbilityType.PRIMARY, 1.5, 1, 0.3);
        primarySpeed = 3;
        secondary = new Ability(AbilityType.SECONDARY, 7, 1, 0);
        secondaryColor = Color.YELLOW;
        secondaryRange = 75;
        ultimate = new Ability(AbilityType.ULTIMATE, 6, 3,100, 4);
        ultimateHits = new HashSet<>();
        passive = new Ability(AbilityType.PASSIVE, 5, 4, 0);
        passiveSpeed = 1.8;

        setEnergyStats(100,100,0.3,true);
        initializeEnergyUpdates();
    }
}
