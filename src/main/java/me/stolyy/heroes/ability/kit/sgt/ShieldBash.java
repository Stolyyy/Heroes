package me.stolyy.heroes.ability.kit.sgt;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class ShieldBash extends Dash {
    private final int windupTicks;
    private final int empoweredWindupTicks;
    private final double empoweredSpeedMultiplier;
    private final SoundEffect windupSound;

    private Vector pendingDirection;
    private int windupRemaining = -1;
    private boolean empowered;

    private double buffDamageSnapshot = 1.0;
    private double buffKnockbackSnapshot = 1.0;

    public ShieldBash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.windupTicks = Configs.requireInt(this.config, "windup_ticks");
        this.empoweredWindupTicks = Configs.requireInt(this.config, "empowered_windup_ticks");
        this.empoweredSpeedMultiplier = Configs.requireDouble(this.config, "empowered_speed_multiplier");
        this.windupSound = new SoundEffect(this.config.getConfigurationSection("visuals.windup_sound"));
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (windupRemaining >= 0 || currentDash != null) return false;
        return super.canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        pendingDirection = hero.getController().getLocation().getDirection().normalize();

        Parry parry = Parry.of(hero);
        empowered = parry != null && parry.isCharged();
        buffDamageSnapshot = hero.getAttributes().getDamageDealtMultiplier();
        buffKnockbackSnapshot = hero.getAttributes().getKnockbackDealtMultiplier();

        int windup = empowered ? empoweredWindupTicks : windupTicks;
        if (windup <= 0) {
            launch(hero);
            return;
        }
        windupRemaining = windup;
        windupSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);
        if (windupRemaining > 0) {
            windupRemaining--;
        } else if (windupRemaining == 0) {
            windupRemaining = -1;
            launch(hero);
        }
    }

    private void launch(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());
        currentDash = new DashInstance(hero, pendingDirection);
    }

    @Override
    protected double dashSpeed() {
        return empowered ? speed * empoweredSpeedMultiplier : speed;
    }

    @Override
    protected double calcDamage(Hero hero, LivingEntity victim) {
        return damage * buffDamageSnapshot;
    }

    @Override
    protected double calcKnockback(Hero hero, LivingEntity victim) {
        return knockback * buffKnockbackSnapshot;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        windupRemaining = -1;
        pendingDirection = null;
        empowered = false;
    }
}
