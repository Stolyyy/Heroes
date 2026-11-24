package me.stolyy.heroes.ability.kit.sgt;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class Ricochet extends Projectile {
    private final double seekRadius;
    private final double largeSeekRadius;
    private final double armingDistance;
    private final double empoweredRangeMultiplier;

    public Ricochet(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.seekRadius = Configs.requireDouble(this.config, "seek_radius");
        this.largeSeekRadius = Configs.requireDouble(this.config, "large_seek_radius");
        this.armingDistance = Configs.requireDouble(this.config, "arming_distance");
        this.empoweredRangeMultiplier = Configs.requireDouble(this.config, "empowered_range_multiplier");
    }

    @Override
    protected ShotParams shotParamsFor(Hero hero) {
        return new ShotParams(
                damage * hero.getAttributes().getDamageDealtMultiplier(),
                knockback * hero.getAttributes().getKnockbackDealtMultiplier(),
                speed, fireSound, trailVisual, hitVisual);
    }

    @Override
    protected ProjectileInstance createInstance(Hero hero, Location start, ShotParams shot) {
        boolean empowered = hero.findAbility(Parry.class).map(Parry::isCharged).orElse(false);
        return new RicochetInstance(hero, start, shot, empowered);
    }

    protected class RicochetInstance extends ProjectileInstance {
        private boolean bounced = false;
        private boolean widened = false;
        private LivingEntity target;
        private final double flightRange;

        RicochetInstance(Hero hero, Location start, ShotParams shot, boolean empowered) {
            super(hero, start, shot);
            this.flightRange = empowered ? range * empoweredRangeMultiplier : range;
        }

        @Override
        protected double flightRange() { return flightRange; }

        @Override
        protected boolean returnPiercesWalls() { return true; }

        @Override
        protected boolean clearHitsOnReturn() { return false; }

        @Override
        protected boolean shouldStartReturn() {
            return target == null && distanceTraveled >= flightRange();
        }

        @Override
        protected boolean steer() {
            if (distanceTraveled < armingDistance) return true;

            if (target != null) {
                boolean lost = target.isDead() || !target.isValid()
                        || hitbox.hasHit(target.getUniqueId())
                        || target.getLocation().distanceSquared(currentLoc) > largeSeekRadius * largeSeekRadius;
                if (lost) target = null;
            }
            if (target == null) {
                target = findTarget(widened ? largeSeekRadius : seekRadius);
            }
            if (target != null) {
                Vector toTarget = target.getBoundingBox().getCenter().subtract(currentLoc.toVector());
                if (toTarget.lengthSquared() > 0.01) {
                    velocity.copy(toTarget.normalize().multiply(shot.speed()));
                }
            }
            return true;
        }

        private @Nullable LivingEntity findTarget(double seekDist) {
            LivingEntity self = hero.getController().getEntity();
            if (self == null) return null;

            Hitbox seeker = widened
                    ? Hitbox.sphere(currentLoc, seekDist)
                    : Hitbox.cone(currentLoc, velocity.clone(), seekDist / 2, seekDist);
            return seeker
                    .exclude(self)
                    .excludeTeammates(self)
                    .get().stream()
                    .filter(e -> !hitbox.hasHit(e.getUniqueId()))
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(currentLoc)))
                    .orElse(null);
        }

        @Override
        protected void onVictimHit(LivingEntity victim, Location hitLoc) {
            widened = true;
            target = null;
        }

        @Override
        protected boolean onBounce(@Nullable BlockFace face, Location wallLoc) {
            if (bounced || face == null) return false;
            bounced = true;
            widened = true;
            target = null;

            wallSound.play(wallLoc, hero.getController());
            wallVisual.play(wallLoc);

            if (face.getModX() != 0) velocity.setX(-velocity.getX());
            if (face.getModY() != 0) velocity.setY(-velocity.getY());
            if (face.getModZ() != 0) velocity.setZ(-velocity.getZ());
            return true;
        }
    }
}
