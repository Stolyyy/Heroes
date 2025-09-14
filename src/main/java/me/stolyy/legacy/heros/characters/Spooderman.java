package me.stolyy.legacy.heros.characters;

import me.stolyy.legacy.heros.HeroEnergy;
import me.stolyy.heroes.heros.configs.AbilityType;
import me.stolyy.legacy.heros.abilities.interfaces.Dash;
import me.stolyy.legacy.heros.abilities.interfaces.Hitscan;
import me.stolyy.heroes.heros.components.UseSneak;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class Spooderman extends HeroEnergy implements Dash, Hitscan, UseSneak {
    public Spooderman(Player player){
        super(player);
    }

    @Override
    public void usePrimaryAbility() {

    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void useSecondaryAbility() {

    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {

    }

    @Override
    public void useUltimateAbility() {
        //symbiote
    }

    @Override
    public void usePassiveSneak(){

    }

    @Override
    protected void stats(){

    }
}
