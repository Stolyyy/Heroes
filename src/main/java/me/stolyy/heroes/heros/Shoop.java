package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Utility.Interactions;
import me.stolyy.heroes.WallDetection;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class Shoop extends Hero implements Projectile, Hitscan, Listener, Energy {

    Player player;
    final double weight = 3;
    Cooldowns cooldowns;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.RANGED;
    }
    double primaryDMG = 1.5;
    double primaryKB = 1;
    double primaryCD = 0.3;
    double secondaryDMG = 7;
    double secondaryRange = 75;
    double secondaryKB = 1;
    double secondaryCD = 0;
    Color secondaryColor = Color.YELLOW;
    double ultimateKB = 3.5;
    double ultimateDMG = 8;
    double passiveKB = 1.1;
    double passiveDMG = 5;
    boolean isOnProjectile = false;
    int ultTime = 4;

    public Shoop(Player player) {
        this.player = player;
        this.cooldowns = new Cooldowns(player, HeroType.RANGED, 100);
        initializeEnergy(player, 0.25);
    }

    @Override
    public void usePrimaryAbility(Player player) {
        if(cooldowns.isPrimaryReady()) {
            Projectile.projectile(player, 2.6, false, 14003, 1, this, AbilityType.PRIMARY);
            cooldowns.usePrimaryAbility(primaryCD);
            player.playSound(player.getLocation(), "shoopdawhoop.active", SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        if (secondaryCD < 5) {
            secondaryCD++;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage("Secondary Charge: " + secondaryCD);
        switch (abilityType) {
            case PRIMARY:
                Interactions.handleInteractions(player.getLocation(), primaryKB, primaryDMG, player, target);
                cooldowns.reduceUltimateCooldown(1);
                break;
            case PASSIVE:
                Interactions.handleInteractions(player.getLocation(), passiveKB, passiveDMG, player, target);
                break;
        }
    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {
    }

    @Override
    public void useSecondaryAbility(Player player) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        Location targetLocation = eyeLocation.clone();
        Bukkit.getLogger().info("Secondary CD: " + secondaryCD);
        if (secondaryCD == 0) {
            return;
        } else if (secondaryCD == 1) {
            secondaryDMG = 2;
            secondaryKB = 1;
            secondaryColor = Color.YELLOW;
        } else if (secondaryCD == 2) {
            secondaryDMG = 4;
            secondaryKB = 2;
            secondaryColor = Color.LIME;
        } else if (secondaryCD == 3) {
            secondaryDMG = 7;
            secondaryKB = 3;
            secondaryColor = Color.BLUE;
        } else if (secondaryCD == 4) {
            secondaryDMG = 10;
            secondaryKB = 4;
            secondaryColor = Color.PURPLE;
        } else if (secondaryCD == 5) {
            secondaryCD = 0;
            secondaryDMG = 15;
            secondaryKB = 5;
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
                List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(targetLocation, 0.5);
                for (Player nearbyPlayer : nearbyPlayers) {
                    if (nearbyPlayer != player) {
                        Interactions.handleInteractions(player.getEyeLocation().getDirection(), secondaryKB, secondaryDMG, player, nearbyPlayer);
                        return;
                    }
                }
            }
            return;
        }
        secondaryCD = 0;
        Hitscan.hitscan(100, eyeLocation, direction, Particle.DUST, secondaryColor, player, this, AbilityType.SECONDARY);
        player.playSound(player.getLocation(), "shoopdawhoop.chargedbeam.small", SoundCategory.MASTER, 2.0f, 1.0f);
    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        switch (abilityType) {
            case SECONDARY:
                Interactions.handleInteractions(player.getLocation().getDirection(), secondaryKB, secondaryDMG, player, target);
                break;
            case ULTIMATE:
                Interactions.handleInteractions(player.getLocation().getDirection(), ultimateKB, ultimateDMG, player, target);
                break;
        }
    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {
    }


    @Override
    public void useUltimateAbility(Player player) {
        if (cooldowns.getUltimateCooldown() == 0) {
            cooldowns.useUltimateAbility();
            Hero h = this;
            player.playSound(player.getLocation(), "shoopdawhoop.crystal", SoundCategory.MASTER, 5.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.0f), (20L));

            new UltTimer(player, ultTime).runTaskTimer(Heroes.getInstance(), 0L, 20L);
            new BukkitRunnable() {
                private int count = 0;
                @Override
                public void run() {
                    count++;
                    if (count >= 20) {
                        this.cancel();
                        return;
                    }
                    Hitscan.hitscan(50, player.getEyeLocation(), player.getLocation().getDirection(), 14004, player, h, AbilityType.ULTIMATE);
                }
            }.runTaskTimer(Heroes.getInstance(), 20L, 5L);
        } else {
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + cooldowns.getUltimateCooldown() + " seconds remaining.");
        }

    }


    @Override
    public void passiveAbility1(Player player) {
        if (getEnergy(player) > 55) {
            removeEnergy(player, 55);
            setCanIncreaseEnergy(player, false);
            ArmorStand armorStand = Projectile.projectile(player, 1.9, false, 14003, 2, this, AbilityType.PASSIVE);
            armorStand.addPassenger(player);
            player.playSound(player.getLocation(), "shoopdawhoop.active", SoundCategory.MASTER, 3.0f, 1.0f);
            isOnProjectile = true;
            new BukkitRunnable() {
                @Override
                public void run() {
                    isOnProjectile = false;
                }
            }.runTaskLater(Heroes.getInstance(), 10L);
            new BukkitRunnable() {
                Location lastLocation = armorStand.getLocation();

                @Override
                public void run() {
                    if (armorStand.isDead() || !armorStand.isValid() || getEnergy(player) <= 0) {
                        player.leaveVehicle();
                        setCanIncreaseEnergy(player, true);
                        this.cancel();
                        return;
                    }
                    if (player.isInsideVehicle()) {
                        Location currentLocation = armorStand.getLocation();
                        double distance = lastLocation.distance(currentLocation);
                        removeEnergy(player, distance);
                        updateXpBar(player);
                        if (getEnergy(player) <= 0) {
                            player.leaveVehicle();
                            setCanIncreaseEnergy(player, true);
                            this.cancel();
                            return;
                        }
                        lastLocation = currentLocation;
                    } else {
                        setCanIncreaseEnergy(player, true);
                        this.cancel();
                    }
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() == player) {
            if (isOnProjectile && getEnergy(player) > 10) {
                event.setCancelled(true);
            }
            Location newLocation = player.getLocation().add(0, 0.75, 0);
            player.teleport(newLocation);
        }
    }

    @Override
    public void passiveAbility2(Player player){
    }
}
