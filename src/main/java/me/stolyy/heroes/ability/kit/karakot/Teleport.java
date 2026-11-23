package me.stolyy.heroes.ability.kit.karakot;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class Teleport extends BaseAbility {
    private final double distance;
    private final double velocityMultiplier;
    private final VisualEffect trailVisual;
    private final VisualEffect initialVisual;
    private final VisualEffect endVisual;
    private final SoundEffect activationSound;

    public Teleport(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.distance = config.getDouble("distance");
        this.velocityMultiplier = config.getDouble("velocity_multiplier");
        this.trailVisual = new VisualEffect(this.config.getConfigurationSection("visuals.trail_visual"));
        this.initialVisual = new VisualEffect(this.config.getConfigurationSection("visuals.initial_visual"));
        this.endVisual = new VisualEffect(this.config.getConfigurationSection("visuals.end_visual"));
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        Location start = hero.getController().getEyeLocation();
        Vector dir = start.getDirection();

        activationSound.play(start);
        initialVisual.play(start);

        Hitbox check = Hitbox.cylinder(start, dir, 0.5, distance)
            .checkWalls(true)
            .pierce(true);

        check.check();

        Location target = check.getWallHitLocation();
        if (target == null) {
            target = start.clone().add(dir.multiply(distance));
        } else {
            target.subtract(dir.normalize().multiply(0.5));
        }

        hero.getController().getEntity().teleport(target);
        hero.getController().setVelocity(dir.multiply(velocityMultiplier));

        trailVisual.play(start, target);
        endVisual.play(target);
    }
}
