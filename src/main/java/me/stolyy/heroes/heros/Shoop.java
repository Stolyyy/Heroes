package me.stolyy.heroes.heros;

import me.stolyy.heroes.Games.GameManager;
import me.stolyy.heroes.Hero;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Interactions;
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

import static org.bukkit.Material.PURPLE_WOOL;

public class Shoop extends Hero implements Projectile, Hitscan, Listener {

    Player player;
    final double weight = 3;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.RANGED;
    }
    boolean inPrimary = false;
    double primaryDMG = 1.5;
    double primaryKB = 1;
    double primaryCD = 0.4;
    double secondaryDMG = 7;
    double secondaryRange = 75;
    double secondaryKB = 1;
    double secondaryCD = 0;
    Color secondaryColor = Color.YELLOW;
    boolean inUltimate = false;
    double ultimateKB = 3.5;
    double ultimateDMG = 8;
    double passiveKB = 1.1;
    double passiveDMG = 5;
    double energy = 100;
    boolean increaseEnergy = true;
    int ultTime = 4;

    public Shoop(Player player) {
        this.player = player;
    }

    @Override
    public void usePrimaryAbility(Player player) {
        if(inPrimary) {
            return;
        }
        inPrimary = true;
        Projectile.projectile(player, 2.3, false, PURPLE_WOOL, 1, this, AbilityType.PRIMARY);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inPrimary = false, (long) (primaryCD * 20L));
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        if (secondaryCD < 5) {
            secondaryCD++;

        }
        player.sendMessage("Secondary Charge: " + secondaryCD);
        switch (abilityType) {
            case PRIMARY:
                Interactions.handleInteractions(player.getLocation(), primaryKB, primaryDMG, player, target);
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
    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
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
        if (inUltimate) {
            return;
        }
        inUltimate = true;
        Hero h = this;
        new UltTimer(player, ultTime).runTaskTimer(Heroes.getInstance(), 0L, 20L);
        new BukkitRunnable() {
            private int count = 0;
            @Override
            public void run() {
                count++;
                if (count >= 20) {
                    inUltimate = false;
                    this.cancel();
                    return;
                }
                Hitscan.hitscan(50, player.getEyeLocation(), player.getLocation().getDirection(), Material.DEAD_BUSH, player, h, AbilityType.ULTIMATE);
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 4L);
    }


    int xpLevels = (int) energy;
    float xpProgress = (float) energy / 100.0f;
    boolean isOnProjectile = false;

    @Override
    public void passiveAbility1(Player player) {

        if (energy > 55) {
            energy -= 55;
            increaseEnergy = false;
            ArmorStand armorStand = Projectile.projectile(player, 1.75, false, Material.PURPLE_WOOL, 2, this, AbilityType.PASSIVE);
            armorStand.addPassenger(player);
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
                    if (armorStand.isDead() || !armorStand.isValid() || energy <= 0) {
                        player.leaveVehicle();
                        increaseEnergy = true;
                        this.cancel();
                        return;
                    }
                    if (player.isInsideVehicle()) {
                        Location currentLocation = armorStand.getLocation();
                        double distance = lastLocation.distance(currentLocation);
                        energy -= distance;
                        xpLevels = (int) energy;
                        xpProgress = (float) (energy / 100.0f);
                        xpProgress = Math.max(0.0f, Math.min(1.0f, xpProgress));
                        player.setLevel(xpLevels);
                        player.setExp(xpProgress);
                        if (energy <= 0) {
                            player.leaveVehicle();
                            increaseEnergy = true;
                            this.cancel();
                            return;
                        }
                        lastLocation = currentLocation;
                    } else {
                        increaseEnergy = true;
                        this.cancel();
                        return;
                    }
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() == player) {
            if (isOnProjectile && energy >10) {
                event.setCancelled(true);
            }
            Location newLocation = player.getLocation().add(0, 0.75, 0);
            player.teleport(newLocation);

        }
    }

    @Override
    public void passiveAbility2(Player player) {

    }

    void updateEnergy() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (energy < 100 && increaseEnergy) {
                    energy += 0.75;
                }
                if (energy > 100) {
                    energy = 100; // Ensure energy does not exceed 100
                }
                xpLevels = (int) energy;
                xpProgress = (float) (energy / 100.0f);

                // Clamp xpProgress to the range [0.0, 1.0]
                xpProgress = Math.max(0.0f, Math.min(1.0f, xpProgress));

                player.setLevel(xpLevels);
                player.setExp(xpProgress);
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }
}
