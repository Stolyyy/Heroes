package me.stolyy.heroes.heros;

import me.stolyy.heroes.AbilityListener;
import me.stolyy.heroes.Games.GameManager;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Utility.Interactions;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;


public class VoidCrawler extends Hero implements Dash, Listener, Energy {

    Player player;
    final double weight = 2;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.MELEE;
    }
    AbilityListener jab;
    double primaryDMG = 6;
    double primaryRange = 9;
    double primaryKB = 1;
    double primaryCD = 2;
    double secondaryDMG = 7;
    double secondaryRange = 15;
    double secondaryKB = 3;
    double secondaryCD = 5;
    int ultCD = 90;
    int ultTime = 12;
    double voidEnergy = 0;
    double voidEnergyDecreasePerTick = -0.5;
    public Cooldowns cooldowns;
    GameManager gameManager;

    public VoidCrawler(Player player) {
        this.player = player;
        this.cooldowns = new Cooldowns(player, HeroType.MELEE, ultCD);
        this.jab = new AbilityListener(Heroes.getInstance().getHeroManager(), Heroes.getInstance().getGameManager());
        this.gameManager = Heroes.getInstance().getGameManager();
        initializeEnergy(player,voidEnergyDecreasePerTick);
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
        player.playSound(player.getLocation(), "melee.sword.meleehit", SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void useSecondaryAbility(Player player) {
        if(cooldowns.isSecondaryReady()) {
            cooldowns.useSecondaryAbility(secondaryCD);
            Location startLocation = player.getLocation();
            Location eyeLocation = player.getEyeLocation();
            Vector direction = player.getEyeLocation().getDirection();
            Location teleportSpot = player.getEyeLocation();
            for (double i = 0; i < secondaryRange; i += 0.5) {
                Location currentLocation = eyeLocation.clone().add(direction.clone().multiply(i));

                if (currentLocation.getBlock().getType().isSolid() || currentLocation.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    player.teleport(currentLocation.subtract(direction.clone().multiply(0.5)));
                    i = secondaryRange +1;
                }
                List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(currentLocation, 1);
                for (Player nearbyPlayer : nearbyPlayers) {
                    if (nearbyPlayer != player) {
                        Interactions.handleInteractions(startLocation, secondaryKB, secondaryDMG, player, nearbyPlayer);
                        i = secondaryRange +1;
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }

                }
                teleportSpot = currentLocation;
            }
            player.teleport(teleportSpot);
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 3, 0.0f);
        }
    }

    @Override
    public void useUltimateAbility(Player player) {
        if (cooldowns.getUltimateCooldown() == 0) {
            player.playSound(player.getLocation(), "duskcrawler.crystal", SoundCategory.MASTER, 5.0f, 1.0f);
            cooldowns.useUltimateAbility();
            List<Player> Targets = (List<Player>) player.getLocation().getNearbyPlayers(75);
            for (Player target : Targets) {
                if (target.getGameMode() == GameMode.SURVIVAL && target != player && gameManager.getPlayerGame(player).arePlayersOnSameTeam(player,target)) {
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
                }
            }.runTaskLater(Heroes.getInstance(), ultTime * 20L);
        } else {
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + cooldowns.getUltimateCooldown() + " seconds remaining.");
        }
    }

    @Override
    public void passiveAbility1(Player player) {

    }

    @Override
    public void passiveAbility2(Player player) {

    }

    @EventHandler
    public void voidEnergyPunch(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() == player) {
            addEnergy(player, 20);
        }
    }

    public void updateAttackDamage() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double currentEnergy = getEnergy(player);
                jab.setJabDamage(player, 5 + currentEnergy/50);
                primaryDMG = 6 + currentEnergy/50;
                secondaryDMG = 7 + currentEnergy/50;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

}
