package me.stolyy.heroes.ability.kit.sgt;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Homing;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Ricochet extends Projectile {
    private final double seekRadius;
    private final double largeSeekRadius;
    private final double armingDistance;
    private final double empoweredRangeMultiplier;

    private double activeBuffDamage = 1.0;
    private double activeBuffKnockback = 1.0;

    public Ricochet(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.seekRadius = Configs.requireDouble(this.config, "seek_radius");
        this.largeSeekRadius = Configs.requireDouble(this.config, "large_seek_radius");
        this.armingDistance = Configs.requireDouble(this.config, "arming_distance");
        this.empoweredRangeMultiplier = Configs.requireDouble(this.config, "empowered_range_multiplier");
    }

    @Override
    protected void fireSingleShot(Hero hero) {
        fireSound.play(hero.getController().getLocation(), hero.getController());
        Parry parry = Parry.of(hero);
        boolean empowered = parry != null && parry.isCharged();
        activeProjectiles.add(new RicochetInstance(hero, getStartLocation(hero), empowered));
    }

    @Override
    protected double calcDamage(Hero hero, LivingEntity victim) {
        return super.calcDamage(hero, victim) * activeBuffDamage;
    }

    @Override
    protected double calcKnockback(Hero hero, LivingEntity victim) {
        return super.calcKnockback(hero, victim) * activeBuffKnockback;
    }

    protected class RicochetInstance extends ProjectileInstance {
        private boolean returning = false;
        private boolean bounced = false;
        private boolean widened = false;
        private double returnDistance = 0;
        private double currentAcquireRadius = seekRadius;
        private LivingEntity target;
        private final Homing returnHoming;
        private final Set<UUID> hitVictims = new HashSet<>();

        private final double flightRange;
        private final double buffDamage;
        private final double buffKnockback;

        RicochetInstance(Hero hero, Location start, boolean empowered) {
            super(hero, start);
            this.flightRange = empowered ? range * empoweredRangeMultiplier : range;
            this.buffDamage = hero.getAttributes().getDamageDealtMultiplier();
            this.buffKnockback = hero.getAttributes().getKnockbackDealtMultiplier();

            hitbox.checkWalls(false).pierce(true);
            this.returnHoming = new Homing(hero.getController().getEntity(), speed, () -> null);
        }

        @Override
        public boolean tick() {
            if (expired) return true;

            if (overLifetime()) return true;
            Location prevLoc = currentLoc.clone();

            if (returning) {
                Vector step = returnHoming.step(currentLoc);
                if (step == null) {
                    cleanup();
                    return true;
                }
                velocity.copy(step);
                currentLoc.add(step);
                returnDistance += step.length();

                hitbox.moveTo(prevLoc).reorient(velocity);
                collectHits();

                if (currentLoc.distanceSquared(hero.getController().getEyeLocation()) < 1.5 * 1.5
                        || returnDistance > flightRange * 2) {
                    cleanup();
                    return true;
                }
            } else {
                steer();
                double stepLen = velocity.length();
                RayTraceResult wall = currentLoc.getWorld().rayTraceBlocks(
                        currentLoc, velocity.clone().normalize(), stepLen + 0.1,
                        FluidCollisionMode.NEVER, true);
                if (wall != null && wall.getHitBlockFace() != null) {
                    if (!bounced) {
                        bounced = true;
                        bounce(wall.getHitBlockFace());
                    } else {
                        startReturn();
                    }
                } else {
                    super.move();
                }

                hitbox.moveTo(prevLoc).reorient(velocity);
                if (collectHits()) {
                    currentAcquireRadius = largeSeekRadius;
                    widened = true;
                    target = null;
                }

                if (target == null && distanceTraveled >= flightRange) {
                    startReturn();
                }
            }

            displayVisuals(prevLoc);
            return false;
        }

        private boolean collectHits() {
            activeBuffDamage = buffDamage;
            activeBuffKnockback = buffKnockback;

            currentDirection = velocity.clone().normalize();
            Set<LivingEntity> hits = hitbox.get();
            for (LivingEntity victim : hits) {
                hitVictims.add(victim.getUniqueId());
            }
            return !hits.isEmpty();
        }

        private void steer() {
            if (distanceTraveled < armingDistance) return;

            if (target != null) {
                boolean lost = target.isDead() || !target.isValid()
                        || hitVictims.contains(target.getUniqueId())
                        || target.getLocation().distanceSquared(currentLoc) > largeSeekRadius * largeSeekRadius;
                if (lost) target = null;
            }
            if (target == null) {
                target = findTarget(currentAcquireRadius);
            }
            if (target != null) {
                Vector toTarget = target.getBoundingBox().getCenter()
                        .subtract(currentLoc.toVector());
                if (toTarget.lengthSquared() > 0.01) {
                    velocity.copy(toTarget.normalize().multiply(speed));
                }
            }
        }

        private LivingEntity findTarget(double radius) {
            LivingEntity self = hero.getController().getEntity();
            if (self == null) return null;

            Hitbox seeker = widened
                    ? Hitbox.sphere(currentLoc, radius)
                    : Hitbox.cone(currentLoc, velocity.clone(), radius / 2, radius);
            return seeker
                    .exclude(self)
                    .excludeTeammates(self)
                    .get().stream()
                    .filter(e -> !hitVictims.contains(e.getUniqueId()))
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(currentLoc)))
                    .orElse(null);
        }

        private void bounce(BlockFace face) {
            wallSound.play(currentLoc, hero.getController());
            wallVisual.play(currentLoc);

            if (face.getModX() != 0) velocity.setX(-velocity.getX());
            if (face.getModY() != 0) velocity.setY(-velocity.getY());
            if (face.getModZ() != 0) velocity.setZ(-velocity.getZ());
            currentAcquireRadius = largeSeekRadius;
            widened = true;
            target = null;
        }

        private void startReturn() {
            returning = true;
        }
    }
}
