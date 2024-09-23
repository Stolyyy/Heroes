package me.stolyy.heroes.heros;

import me.stolyy.heroes.AbilityListener;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Utility.Interactions;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class Blud extends Hero implements Hitscan, Energy, Dash, Projectile {

    Player player;
    final double weight = 3;
    @Override
    public double getWeight() {return weight;}
    @Override
    public HeroType getHeroType() {return HeroType.HYBRID;}
    private PrimaryType primaryType;
    Cooldowns cooldowns;

    public Blud(Player player) {
        this.player = player;
        this.cooldowns = new Cooldowns(player, HeroType.HYBRID, 90);
        initializeEnergy(player,1);
    }

    enum PrimaryType{
        DASH,
        PIERCE,
        BULLET
    }

    @Override
    public void usePrimaryAbility(Player player) {
        switch(primaryType) {
            case DASH:
                if(cooldowns.isPrimaryReady()) {
                    Dash.onDash(player, this, AbilityType.PRIMARY, 7);
                    cooldowns.usePrimaryAbility(1.75);
                    removeEnergy(player, 60);
                }
                break;
            case PIERCE:
                if(cooldowns.isPrimaryReady()) {
                    Hitscan.hitscan(100, player.getEyeLocation(), player.getEyeLocation().getDirection(), Particle.DUST, Color.RED, player, this, AbilityType.PRIMARY);
                    cooldowns.usePrimaryAbility(0.1);
                    removeEnergy(player, 3);
                }
                break;
            case BULLET:
                if(cooldowns.isPrimaryReady()) {
                    Projectile.projectile(player, 1.8, false, 17001, 1.5, this, AbilityType.PRIMARY);
                    cooldowns.usePrimaryAbility(0.5);
                    removeEnergy(player, 30);
                }
                break;
        }
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType) {
        Interactions.handleInteractions(player.getEyeLocation().getDirection(), 1, 6, player, target);
        player.playSound(player.getLocation(), "melee.sword.meleehit", SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        Interactions.handleInteractions(player.getLocation().getDirection(), 0.3, 0.5, player, target);
    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {

    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        Interactions.handleInteractions(player.getLocation().getDirection(), 3.5, 5, player, target);
    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {

    }

    @Override
    public void useSecondaryAbility(Player player) {
        switch(primaryType) {
            case DASH:
                if(player.isSneaking()){
                    primaryType = PrimaryType.BULLET;
                } else {
                    primaryType = PrimaryType.PIERCE;
                }
                break;
            case PIERCE:
                if(player.isSneaking()){
                    primaryType = PrimaryType.DASH;
                } else {
                    primaryType = PrimaryType.BULLET;
                }
                break;
            case BULLET:
                if(player.isSneaking()){
                    primaryType = PrimaryType.PIERCE;
                } else {
                    primaryType = PrimaryType.DASH;
                }
                break;
        }
    }

    @Override
    public void useUltimateAbility(Player player) {

    }

    @Override
    public void passiveAbility1(Player player) {

    }

    @Override
    public void passiveAbility2(Player player) {

    }
}
