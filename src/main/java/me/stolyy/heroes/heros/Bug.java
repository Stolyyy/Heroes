package me.stolyy.heroes.heros;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;

public class Bug extends Hero implements Dash, Energy, Projectile {
    double weight = 2;
    @Override
    public double getWeight() {return weight;}
    @Override
    public HeroType getHeroType() {return HeroType.HYBRID;}
    double primaryDMG = 6;
    double secondaryMultiplier = 1;
    Set<Charms> charms;

    public Bug(){

    }


    @Override
    public void usePrimaryAbility(Player player) {
        Dash.onDash(player, this, AbilityType.PRIMARY, 8);
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void useSecondaryAbility(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch < -45) {
            //cone attack like frostbreath
        } else if(pitch > 45){
            Dash.onDash(player, this, AbilityType.SECONDARY, 20);
        } else{
            Projectile.projectile(player, 3, false, 18003, 1.5 * secondaryMultiplier, this, AbilityType.SECONDARY);
        }
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {

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
