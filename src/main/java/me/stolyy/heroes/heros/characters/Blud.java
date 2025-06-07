package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.heros.abilities.*;
import me.stolyy.heroes.heros.abilities.interfaces.Dash;
import me.stolyy.heroes.heros.abilities.interfaces.Hitscan;
import me.stolyy.heroes.heros.abilities.interfaces.Projectile;
import me.stolyy.heroes.heros.abilities.AbilityType;
import org.bukkit.*;
import org.bukkit.entity.Player;

public class Blud extends HeroEnergy implements Hitscan, Dash, Projectile {
    private PrimaryType primaryType;
    private Ability dash;
    private double dashLength;
    private Ability bullet;
    private double bulletRadius;
    private double bulletSpeed;
    private Ability pierce;


    public Blud(Player player){
        super(player);
    }

    //swaps between primaries, uses energy for cooldowns
    @Override
    public void usePrimaryAbility() {
        /*switch (primaryType){
            case DASH -> {
                if(!dash.ready || energy < 60) return;
                dash(player, AbilityType.PRIMARY, dashLength);
                cooldown(dash);
                energy -= 60;
            } case BULLET -> {
                if(!bullet.ready || energy < 30) return;
                projectile(player, AbilityType.PRIMARY, bulletSpeed, bulletRadius, false, 17001);
                cooldown(bullet);
                energy -= 30;
            } case PIERCE -> {
                if(!pierce.ready || energy < 2) return;
                hitscan(player, AbilityType.PRIMARY, 0.5, 100, Particle.DUST, Color.RED);
                cooldown(pierce);
                energy -= 2;
            }
        }*/
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {

    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {

    }

    @Override
    public void useSecondaryAbility() {
        /*switch(primaryType) {
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
        }*/
    }

    @Override
    public void useUltimateAbility() {

    }

    @Override
    protected void stats() {
        /*heroType = HeroType.HYBRID;
        weight = 3.5;
        dash = new Ability(AbilityType.PRIMARY, 6, 2,1.5);
        dashLength = 7;
        bullet = new Ability(AbilityType.PRIMARY, 7, 2, 0.6);
        bulletRadius = 1.5;
        bulletSpeed = 2;
        pierce = new Ability(AbilityType.PRIMARY, 1, 0.5, 0.1);
        primaryType = PrimaryType.DASH;
        ultimate = new Ability(AbilityType.ULTIMATE, 0,0,90, 10);
        energy = 100;
        maxEnergy = 100;
        energyPerTick = 1.5;
        initializeEnergyUpdates();*/
    }

    enum PrimaryType{
        DASH,
        BULLET,
        PIERCE
    }
}
