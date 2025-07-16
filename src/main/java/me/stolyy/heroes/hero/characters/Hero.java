package me.stolyy.heroes.hero.characters;

import me.stolyy.heroes.hero.abilities.Ability;
import me.stolyy.heroes.hero.components.*;
import me.stolyy.heroes.hero.configs.ConfigManager;
import me.stolyy.heroes.hero.configs.HeroConfig;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.*;

public abstract class Hero {
    protected final Player player;
    protected final HeroConfig config;

    protected Ability primary;
    protected Ability secondary;
    protected Ability ultimate;

    protected double damageMultiplier = 1.0;
    protected double knockbackMultiplier = 1.0;

    protected Equipment equipment;
    protected Movement movement;
    protected Jab jab;
    protected Display display;
    protected Energy energy;
    protected Ammo ammo;
    protected CooldownManager cooldownManager;

    protected Set<OnTick> onTickRunnables;

    public Hero(Player player) {
        this.player = player;
        this.config = ConfigManager.getHeroConfig(this.getClass());
        onTickRunnables = new HashSet<>();

        //components
        equipment = new Equipment(this, config);
        movement = new Movement(this, config.movementConfig());
        jab = new Jab(this, config.jabConfig());
        display = new Display(this);
        energy = new Energy(this, config.energyConfig());
        ammo = new Ammo(this, config.ammoConfig());
        cooldownManager = new CooldownManager();

        setBaseHealth(config.maxHealth());

        defineAbilities();

        equipment.equip();
    }

    protected abstract void defineAbilities();

    public void usePrimary(){ if (primary != null) primary.execute(); }
    public void useSecondary(){ if (secondary != null) secondary.execute(); }
    public void useUltimate(){if (ultimate != null) ultimate.execute(); }

    public void jab(Player target) {
        jab.jab(target);
        onMeleeHit();
    }
    public void onToggleFlight() {
        if(getMovement().canDoubleJump()) getMovement().performDoubleJump();
    }
    public void onLanding() {
        getMovement().resetDoubleJumps();
    }

    protected void onMeleeHit() {}
    protected void onRangedHit(){}

    protected void onTick() {for (OnTick tick : onTickRunnables) tick.onTick(); }
    public void clean() { for (OnTick tick : onTickRunnables) tick.clean(); }

    public void onStart() {}
    public void onDeath() {}
    public void onRespawn() {}
    public void onElimination() { clean();}

    private void setBaseHealth(double health) {
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(health);
        player.setHealth(health);
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
    }

    public Hero setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
        return this;
    }
    public Hero setKnockbackMultiplier(double knockbackMultiplier) {
        this.knockbackMultiplier = knockbackMultiplier;
        return this;
    }

    public HeroConfig getConfig() { return config; }
    public final Player getPlayer() { return player; }

    public double getDamageMultiplier() { return damageMultiplier; }
    public double getKnockbackMultiplier() { return knockbackMultiplier; }

    public Ability getPrimary() { return primary; }
    public Ability getSecondary() { return secondary; }
    public Ability getUltimate() { return ultimate; }

    public Equipment getEquipment() { return equipment; }
    public Movement getMovement() { return movement; }
    public Display getDisplay() { return display; }
    public Jab getJab() { return jab; }
    public Energy getEnergy() { return energy; }
    public Ammo getAmmo() { return ammo; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
}
