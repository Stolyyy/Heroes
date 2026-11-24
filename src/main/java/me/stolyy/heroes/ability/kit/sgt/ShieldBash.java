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
    private final long empoweredWindupMs;
    private final double empoweredSpeedMultiplier;
    private final SoundEffect windupSound;

    private Vector pendingDirection;
    private boolean empowered;

    private double buffDamageSnapshot = 1.0;
    private double buffKnockbackSnapshot = 1.0;

    public ShieldBash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.empoweredWindupMs = (long) (Configs.requireDouble(this.config, "empowered_windup") * 1000);
        this.empoweredSpeedMultiplier = Configs.requireDouble(this.config, "empowered_speed_multiplier");
        this.windupSound = new SoundEffect(Configs.requireSection(this.config, "visuals.windup_sound"));
    }

    @Override
    protected void onCast(Hero hero) {
        pendingDirection = hero.getController().getLocation().getDirection().normalize();
        empowered = hero.findAbility(Parry.class).map(Parry::isCharged).orElse(false);
        buffDamageSnapshot = hero.getAttributes().getDamageDealtMultiplier();
        buffKnockbackSnapshot = hero.getAttributes().getKnockbackDealtMultiplier();
    }

    @Override
    protected long windupMs(Hero hero) {
        return empowered ? empoweredWindupMs : windupMs;
    }

    @Override
    protected void onWindupStart(Hero hero) {
        windupSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected Vector dashDirection(Hero hero) {
        return pendingDirection != null ? pendingDirection : super.dashDirection(hero);
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
        pendingDirection = null;
        empowered = false;
    }
}
