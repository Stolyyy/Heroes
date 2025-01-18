package me.stolyy.heroes.heros;

import org.bukkit.entity.Player;

public class Punishgers extends Hero{
    @Override
    public double getWeight() {
        return 0;
    }

    @Override
    public HeroType getHeroType() {
        return null;
    }

    @Override
    public void usePrimaryAbility(Player player) {
        //assault rifle or shot gun
    }

    @Override
    public void useSecondaryAbility(Player player) {
        //fat turrit
    }

    @Override
    public void useUltimateAbility(Player player) {
        //steroid fire (but slight slowness)
    }

    @Override
    public void passiveAbility1(Player player) {
        //shift to swap
    }

    @Override
    public void passiveAbility2(Player player) {

    }
}
