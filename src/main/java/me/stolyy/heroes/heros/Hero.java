package me.stolyy.heroes.heros;

import me.stolyy.heroes.hero.config.HeroType;
import me.stolyy.heroes.heros.abilities.Ability;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public abstract class Hero {
    protected final Player player;
    private HeroType heroType;
    private double weight;

    protected final List<BukkitTask> activeTasks = new LinkedList<>();

    private double jabCooldown = 0.5;
    private double jabDamage = 1.0;
    private double jabReach = 5.0;

    private int maxDoubleJumps = 2;

    private double damageMultiplier = 1.0;
    private double knockbackMultiplier = 1.0;

    protected Ability primary;
    protected Ability secondary;
    protected Ability ultimate;

    public Hero(Player player){
        this.player = player;
        stats();
        baseStats();
    }

    public abstract void usePrimaryAbility();
    public abstract void useSecondaryAbility();
    public abstract void useUltimateAbility();

    protected abstract void stats();

    private void baseStats() {
        switch (heroType) {
            case MELEE -> jabDamage = 5.0;
            case HYBRID -> jabDamage = 4.0;
            case RANGED -> jabDamage = 2.0;
        }
    }

    protected Hero setHeroType(HeroType heroType) {
        this.heroType = heroType;
        return this;
    }
    protected Hero setWeight(double weight) {
        this.weight = weight;
        return this;
    }
    protected Hero setJabCooldown(double jabCooldown) {
        this.jabCooldown = jabCooldown;
        return this;
    }
    protected Hero setJabDamage(double jabDamage) {
        this.jabDamage = jabDamage;
        return this;
    }
    protected Hero setJabReach(double jabReach) {
        this.jabReach = jabReach;
        Objects.requireNonNull(player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)).setBaseValue(jabReach);
        return this;
    }
    protected Hero setMaxDoubleJumps(int maxDoubleJumps) {
        this.maxDoubleJumps = maxDoubleJumps;
        return this;
    }
    protected Hero setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
        return this;
    }
    protected Hero setKnockbackMultiplier(double knockbackMultiplier) {
        this.knockbackMultiplier = knockbackMultiplier;
        return this;
    }

    public Player player() {
        return player;
    }
    public HeroType heroType() {
        return heroType;
    }
    public double weight() {
        return weight;
    }
    public double jabCooldown() {
        return jabCooldown;
    }
    public double jabDamage() {
        return jabDamage;
    }
    public double jabReach() {
        return jabReach;
    }
    public int maxDoubleJumps() {
        return maxDoubleJumps;
    }
    public double damageMultiplier() {
        return damageMultiplier;
    }
    public double knockbackMultiplier() {
        return knockbackMultiplier;
    }
}