package me.stolyy.heroes.hero.components;

import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.configs.JabConfig;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.effects.Sounds;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Objects;

public class Jab {
    private final Hero hero;
    private final JabConfig config;

    private double damage, knockback, cooldown, reach; // cooldown in seconds
    private final String hitSound, swingSound;

    public Jab(Hero hero, JabConfig jabConfig) {
        this.hero = hero;
        this.config = jabConfig;

        this.damage = config.damage();
        this.knockback = config.knockback();
        this.cooldown = config.cooldown();
        this.reach = config.reach();
        this.hitSound = config.hitSound();
        this.swingSound = config.swingSound();

        Objects.requireNonNull(hero.getPlayer().getAttribute(Attribute.ENTITY_INTERACTION_RANGE)).setBaseValue(reach);
    }

    public void jab(Player target){
        Interactions.handleStaticInteraction(damage, knockback, hero.getPlayer(), target);
        Sounds.playSoundToWorld(hero.getPlayer(), hitSound, 1.0f, 1.0f);
    }

    public void onSwing(){
        Sounds.playSoundToWorld(hero.getPlayer(), swingSound, 1.0f, 1.0f);
    }

    public double getCooldown() {
        return cooldown;
    }

    public Jab setDamage(double damage) {
        this.damage = damage;
        return this;
    }

    public Jab setKnockback(double knockback) {
        this.knockback = knockback;
        return this;
    }

    public Jab setReach(double reach) {
        this.reach = reach;
        Objects.requireNonNull(hero.getPlayer().getAttribute(Attribute.ENTITY_INTERACTION_RANGE)).setBaseValue(reach);
        return this;
    }

    public Jab setCooldown(double cooldown) {
        this.cooldown = cooldown;
        return this;
    }
}
