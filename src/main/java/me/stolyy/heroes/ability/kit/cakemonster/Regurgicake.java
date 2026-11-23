package me.stolyy.heroes.ability.kit.cakemonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.VectorUtil;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class Regurgicake extends Projectile {
    private final double maxAngle;
    private final SoundEffect activationSound;

    public Regurgicake(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.maxAngle = config.getDouble("max_angle");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero){
        super.perform(hero);
        activationSound.play(hero.getController());
    }

    @Override
    protected Vector getDirection(Location location) {
        return VectorUtil.randomSpread(super.getDirection(location), maxAngle);
    }
}
