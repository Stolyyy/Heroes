package me.stolyy.heroes.ability.kit.cakemonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.VectorUtil;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class CakeStorm extends Projectile {
    private final int delayBetweenShots;
    private final double maxAngle;
    private final SoundEffect activationSound;
    private int elapsed = 0;
    private boolean isActive;


    public CakeStorm(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.delayBetweenShots = config.getInt("delay_between_shots");
        this.maxAngle = config.getDouble("max_angle");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero){
        isActive = true;
        elapsed = 0;
        activationSound.play(hero.getController());
    }

    @Override
    protected void onTick(Hero hero){
        super.onTick(hero);
        if (!isActive) return;

        if(!getCooldownObject().isInUse()) {
            isActive = false;
            return;
        }

        elapsed++;

        if (elapsed * 50L > delayBetweenShots) {
            fire(hero);
            elapsed = 0;
        }
    }

    private void fire(Hero hero){
        super.perform(hero);
    }

    @Override
    protected Vector getDirection(Location location) {
        return VectorUtil.randomSpread(super.getDirection(location), maxAngle);
    }
}
