package me.stolyy.heroes.ability.kit.tinman;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.VectorUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class TinLaser extends Projectile {
    private final double maxAngle;

    public TinLaser(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.maxAngle = config.getDouble("max_angle", 0.0);
    }

    @Override
    protected Vector getDirection(Location location) {
        return VectorUtil.randomSpread(super.getDirection(location), maxAngle);
    }
}