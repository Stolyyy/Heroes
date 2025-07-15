package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.heros.abilities.interfaces.Dash;
import me.stolyy.heroes.heros.abilities.interfaces.Shockwave;
import me.stolyy.heroes.hero.config.AbilityType;
import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.heros.abilities.interfaces.Projectile;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;

public class Bulk extends Hero implements Listener, Projectile, Dash, Shockwave {
    private double secondaryLength;
    private double slamEnergy;
    private int ultSlams;


    public Bulk(Player player) {
        super(player);
    }

    @Override
    public void usePrimaryAbility() {

    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {

    }

    private void carExplode(Location location){

    }

    @Override
    public void useSecondaryAbility() {

    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void useUltimateAbility() {

    }

    @Override
    public void onShockwaveHit(Player target, AbilityType abilityType) {

    }

    @Override
    protected void stats() {

    }
}