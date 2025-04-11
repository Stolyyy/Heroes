package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.*;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.heros.*;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.abilities.AbilityListener;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.heros.abilities.Dash;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;


public class VoidCrawler extends HeroEnergy implements Dash {
    private double primaryRange;
    private double secondaryRange;


    public VoidCrawler(Player player) {
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
        Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg, primary.kb, player, target);
        player.playSound(player.getLocation(), "melee.sword.meleehit", SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void useSecondaryAbility() {
        if(!secondary.ready) return;
        cooldown(secondary);

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

            //hit logic
            List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(currentLocation, 1);
            for (Player nearbyPlayer : nearbyPlayers) {
                if (nearbyPlayer != player) {
                    Interactions.handleInteraction(startLocation, secondary.dmg, secondary.kb, player, nearbyPlayer);
                    i = secondaryRange +1;
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }

            }
            teleportSpot = currentLocation;
        }

        player.teleport(teleportSpot);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 3, 0.0f);
    }


    @Override
    public void useUltimateAbility() {
        if(ultimate.inUse || !ultimate.ready){
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + (int) ultimate.timeUntilUse + " seconds remaining.");
            return;
        }

        player.playSound(player.getLocation(), "duskcrawler.crystal", SoundCategory.MASTER, 5.0f, 1.0f);
        ultTimer();
        AbilityListener.setJabCooldown(player, 300);
        AbilityListener.setJabReach(player ,6.5);
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.18);
        //perma 100 void energy
        energy = 100;
        canIncreaseEnergy = false;

        //darkness
        List<Player> Targets = (List<Player>) player.getLocation().getNearbyPlayers(75);
        for (Player target : Targets) {
            boolean onSameTeam = GameManager.getPlayerGame(player).getPlayerTeams().get(target).equals(GameManager.getPlayerGame(player).getPlayerTeams().get(player));
            if (target.getGameMode() == GameMode.ADVENTURE && !onSameTeam) {
                PotionEffect darknessEffect = new PotionEffect(PotionEffectType.DARKNESS, 20 * (int) ultimate.duration, 0);
                target.addPotionEffect(darknessEffect);
            }
        }

        primary.cd = 1.5;
        secondary.cd = 3.5;

        //end logic
        new BukkitRunnable() {
            @Override
            public void run() {
                AbilityListener.setJabCooldown(player, 500);
                AbilityListener.setJabReach(player, 5);
                player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.14);
                canIncreaseEnergy = true;
                cooldown(ultimate);
            }
        }.runTaskLater(Heroes.getInstance(), (int) ultimate.duration * 20L);
    }

    @Override
    public void passiveAbility1() {

    }

    @Override
    public void passiveAbility2() {

    }

    @Override
    public void onPunch(){
        int reduce = 0;
        switch (heroType) {
            case MELEE -> reduce = 3;
            case HYBRID -> reduce = 1;
        }
        ultimate.timeUntilUse -= reduce;
        energy = Math.min(maxEnergy, energy + 20);
    }

    public void updateAttackDamage() {
        Hero h = this;
        new BukkitRunnable() {
            @Override
            public void run() {
                if(HeroManager.getHero(player) != h){
                    this.cancel();
                    AbilityListener.setJabDamage(player,-1);
                    return;
                }

                AbilityListener.setJabDamage(player,
                        5
                                + energy / 50);
                primary.dmg = 6 + energy/50;
                secondary.dmg = 7 + energy/50;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    @Override
    protected void stats(){
        weight = 2;
        heroType = HeroType.MELEE;
        primary = new Ability(AbilityType.PRIMARY, 6, 2, 2);
        primaryRange = 8;
        secondary = new Ability(AbilityType.SECONDARY, 7, 2.5, 5);
        secondaryRange = 15;
        ultimate = new Ability(AbilityType.ULTIMATE, 0, 0, 90, 10);

        setEnergyStats(0,100,-0.5,true);
        initializeEnergyUpdates();
    }
}
