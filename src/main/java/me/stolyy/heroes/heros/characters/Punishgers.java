package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.heros.HeroType;
import org.bukkit.entity.Player;

public class Punishgers extends Hero {
    @Override
    public double getWeight() {
        return 0;
    }

    @Override
    public HeroType getHeroType() {
        return null;
    }

    @Override
    public void usePrimaryAbility() {
        //assault rifle or shot gun
    }

    @Override
    public void useSecondaryAbility() {
        //fat turrit
    }

    @Override
    public void useUltimateAbility() {
        //steroid fire (but slight slowness)
    }

    @Override
    public void passiveAbility1() {
        //shift to swap
    }

    @Override
    public void passiveAbility2() {

    }
}
