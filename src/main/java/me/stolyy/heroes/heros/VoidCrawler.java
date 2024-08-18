package me.stolyy.heroes.heros;

import me.stolyy.heroes.Games.GameManager;
import me.stolyy.heroes.Hero;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Interactions;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.checkerframework.checker.units.qual.A;

import java.util.List;


public class VoidCrawler extends Hero implements Dash, Listener {

    Player player;
    final double weight = 2;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.MELEE;
    }
    AbilityListener jab = new AbilityListener(Heroes.getInstance().getHeroManager(), Heroes.getInstance().getGameManager());
    boolean inPrimary = false;
    double primaryDMG = 6;
    double primaryRange = 9;
    double primaryKB = 0.5;
    double primaryCD = 2;
    double secondaryDMG = 7;
    double secondaryRange = 18;
    double secondaryKB = 0.8;
    double secondaryCD = 5;
    double ultCD = 90;
    boolean inUltimate = false;
    int ultTime = 12;
    double voidEnergy = 0;
    double voidEnergyDecreasePerTick = 0.5;
    boolean inSecondary = false;

    public VoidCrawler(Player player) {
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
        Location startLocation = player.getLocation();
        Location eyeLocation = player.getEyeLocation();
        Vector direction = player.getEyeLocation().getDirection();
        Location teleportSpot = player.getEyeLocation();
        for (double i = 0; i < secondaryRange; i += 0.5) {
            Location currentLocation = eyeLocation.clone().add(direction.clone().multiply(i));

            if (currentLocation.getBlock().getType().isSolid() || currentLocation.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                player.teleport(currentLocation.subtract(direction.clone().multiply(0.5)));
                player.teleport(teleportSpot);
                i = secondaryRange +1;
            }
            List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(currentLocation, 1);
            for (Player nearbyPlayer : nearbyPlayers) {
                if (nearbyPlayer != player) {
                    Interactions.handleInteractions(startLocation, secondaryKB, secondaryDMG, player, nearbyPlayer);
                    player.teleport(currentLocation);
                    i = secondaryRange +1;
                }

            }
            teleportSpot = currentLocation;
        }
        player.teleport(teleportSpot);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inSecondary = false, (long) (secondaryCD * 20L));
    }

    @Override
    public void useUltimateAbility(Player player) {
        if (inUltimate) {
            return;
        }
        inUltimate = true;
        List<Player> Targets = (List<Player>) player.getLocation().getNearbyPlayers(75);
        for (Player target : Targets) {
            if (target.getGameMode() == GameMode.SURVIVAL && target != player   ) {
                PotionEffect darknessEffect = new PotionEffect(PotionEffectType.DARKNESS, 20 * ultTime, 0);
                target.addPotionEffect(darknessEffect);
            }
        }
        jab.setJabCooldown(player, 300);
        jab.setJabReach(player, 6.5);
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.18);
        primaryCD = 1.5;
        secondaryCD = 3.5;
        voidEnergy = 100;
        new UltTimer(player, ultTime).runTaskTimer(Heroes.getInstance(), 0L, 20L);
        new BukkitRunnable() {
            @Override
            public void run() {
                jab.setJabCooldown(player, 500);
                jab.setJabReach(player, 5.5);
                player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(.14);
                primaryCD = 2;
                secondaryCD = 5;
                inUltimate = false;;
            }
        }.runTaskLater(Heroes.getInstance(), ultTime * 20L);
    }

    @Override
    public void passiveAbility1(Player player) {

    }

    @Override
    public void passiveAbility2(Player player) {

    }

    int xpLevels = (int) voidEnergy;
    double xpProgress = voidEnergy / 100.0f;

    @EventHandler
    public void voidEnergyPunch(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() == player) {
            voidEnergy += 20;
            xpLevels = (int) voidEnergy;
            xpProgress = voidEnergy/ 100.0f;
            xpProgress = Math.max(0.0f, Math.min(1.0f, xpProgress));
            player.setLevel(xpLevels);
            player.setExp((float) xpProgress);
            if(voidEnergy > 100) {
                voidEnergy = 100;
            }
        }
    }

    public void lowerVoidEnergy() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (voidEnergy > 0 && !inUltimate) {
                    voidEnergy -= voidEnergyDecreasePerTick;
                }
                jab.setJabDamage(player, 5 + voidEnergy/50);
                primaryDMG = 6 + voidEnergy/50;
                secondaryDMG = 7 + voidEnergy/50;
                xpLevels = (int) voidEnergy;
                xpProgress = voidEnergy / 100.0f;
                player.setLevel(xpLevels);
                player.setExp((float) xpProgress);
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }
}
