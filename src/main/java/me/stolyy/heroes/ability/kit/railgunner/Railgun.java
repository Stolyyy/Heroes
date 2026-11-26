package me.stolyy.heroes.ability.kit.railgunner;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class Railgun extends Projectile {
    private final double headshotTolerance;
    private final double headshotDamageMultiplier;
    private final SoundEffect headshotSound;

    private boolean currentShotHeadshot;

    public Railgun(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.headshotTolerance = Configs.requireDouble(this.config, "headshot_tolerance");
        this.headshotDamageMultiplier = Configs.requireDouble(this.config, "headshot_damage_multiplier");

        this.headshotSound = new SoundEffect(Configs.requireSection(this.config, "visuals.headshot_sound"));
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        currentShotHeadshot = Math.abs(victim.getEyeLocation().getY() - hitLoc.getY()) < headshotTolerance;
        super.onHit(attacker, victim, hitLoc);
    }

    @Override
    protected double calcDamage(Hero hero, LivingEntity victim) {
        double base = super.calcDamage(hero, victim);
        return currentShotHeadshot ? base * headshotDamageMultiplier : base;
    }

    @Override
    protected SoundEffect hitSoundFor(Hero attacker, LivingEntity victim, Location hitLoc) {
        return currentShotHeadshot ? headshotSound : hitSound;
    }
}
