package me.stolyy.heroes.ability.kit.karakot;

import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.WallDetection;
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
        this.distance = Configs.requireDouble(this.config, "distance");
        this.velocityMultiplier = Configs.requireDouble(this.config, "velocity_multiplier");
        this.trailVisual = VisualEffect.optional(this.config, "visuals.trail_visual");
        this.initialVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.initial_visual"));
        this.endVisual = VisualEffect.optional(this.config, "visuals.end_visual");
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        Location start = hero.getController().getEyeLocation();
        Vector dir = start.getDirection();
        double maxDist = PracticeManager.practiceArenaOf(hero) != null
                ? Math.max(0.0, PracticeManager.clampPracticeRange(hero, start, dir, distance) - 0.5)
                : distance;

        activationSound.play(start);
        initialVisual.play(start);

        Hitbox check = Hitbox.cylinder(start, dir, 0.5, maxDist)
            .checkWalls(true)
            .pierce(true);

        check.check();

        Location target = check.getWallHitLocation();
        if (target == null) {
            target = start.clone().add(dir.clone().multiply(maxDist));
        } else {
            target.subtract(dir.clone().normalize().multiply(0.5));
        }
        if (!PracticeManager.isWithinPracticeBounds(hero, target)) {
            target = hero.getController().getLocation();
        }

        WallDetection.safeTeleport(hero.getController(), target);
        hero.getController().setVelocity(dir.clone().multiply(velocityMultiplier));

        trailVisual.play(start, target);
        endVisual.play(target);
    }
}
