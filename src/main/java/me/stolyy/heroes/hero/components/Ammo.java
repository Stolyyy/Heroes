package me.stolyy.heroes.hero.components;

import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.data.HeroData.AmmoData;

public class Ammo{
    private final Hero hero;
    private final AmmoData data;

    private int maxAmmo;
    private int currentAmmo;

    public Ammo(Hero hero, AmmoData data) {
        this.hero = hero;
        this.data = data;

        currentAmmo = maxAmmo = data.getMaxAmmo();

    }
}
