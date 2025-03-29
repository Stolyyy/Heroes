package me.stolyy.heroes.heros;

import org.bukkit.entity.Player;

public abstract class Hero {
    public Player player;
    public abstract double getWeight();
    public abstract HeroType getHeroType();
    public abstract void usePrimaryAbility();
    public abstract void useSecondaryAbility();
    public abstract void useUltimateAbility();
    public abstract void passiveAbility1();
    public abstract void passiveAbility2();
    public Hero getHero() {
        return this;
    }

}