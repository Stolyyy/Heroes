package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.abilities.Dash;
import me.stolyy.heroes.heros.abilities.Shockwave;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.WallDetection;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.heros.HeroType;
import me.stolyy.heroes.heros.abilities.Projectile;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    public void passiveAbility1() {

    }

    @Override
    public void passiveAbility2() {

    }

    @Override
    protected void stats() {

    }
}