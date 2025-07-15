package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.hero.config.AbilityType;
import me.stolyy.heroes.heros.abilities.interfaces.Dash;
import me.stolyy.heroes.heros.abilities.interfaces.Hitscan;
import me.stolyy.heroes.hero.components.UseSneak;
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
