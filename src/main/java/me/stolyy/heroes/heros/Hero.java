package me.stolyy.heroes.heros;

import org.bukkit.entity.Player;

public abstract class Hero {
    public abstract double getWeight();
    public abstract HeroType getHeroType();
    public abstract void usePrimaryAbility(Player player);
    public abstract void useSecondaryAbility(Player player);
    public abstract void useUltimateAbility(Player player);
    public abstract void passiveAbility1(Player player);
    public abstract void passiveAbility2(Player player);
    public Hero getHero() {
        return this;
    }

}