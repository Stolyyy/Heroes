package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.physics.Homing;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MarauderSaberThrow extends Projectile {
    private final Map<UUID, SaberInstance> activeSabers = new HashMap<>();

    public MarauderSaberThrow(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    public boolean execute(Hero hero) {
        SaberInstance active = activeSabers.get(hero.getController().getUniqueId());
        if (active != null && !active.isReturning()) {
            active.startReturn();
            return true;
        }
        return super.execute(hero);
    }

    @Override
    public void perform(Hero hero) {
        fireSound.play(hero.getController().getLocation());
        SaberInstance saber = new SaberInstance(hero, hero.getController().getEyeLocation());
        activeProjectiles.add(saber);
        activeSabers.put(hero.getController().getUniqueId(), saber);
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);
        activeSabers.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    protected class SaberInstance extends ProjectileInstance {
        private boolean returning = false;
        private double returnDistanceTraveled = 0;
        private float currentRotation = 0;
        private ItemDisplay display;

        private final Homing returnHoming;

        public SaberInstance(Hero hero, Location start) {
            super(hero, start);
            this.display = spawnSaberDisplay(start);
            this.returnHoming = new Homing(hero.getController().getEntity(), speed, () -> null);
        }

        private ItemDisplay spawnSaberDisplay(Location start) {
            return projVisual.spawnDisplay(start, hero.getSkin().getId());
        }

        public void startReturn() {
            if (returning) return;
            returning = true;
            hitbox.clearHitHistory();

            hitbox.checkWalls(false);
        }

        public boolean isReturning() {
            return returning;
        }

        @Override
        public boolean tick() {
            if (expired) return true;
            if (overLifetime()) return true;

            if (!returning && distanceTraveled >= range) {
                startReturn();
            }

            Location prevLoc = currentLoc.clone();
            move();

            if (returning) {

                if (currentLoc.distanceSquared(hero.getController().getEyeLocation()) < 1.5 * 1.5) {
                    cleanup();
                    return true;
                }
                if (returnDistanceTraveled > range * 2) {
                    cleanup();
                    return true;
                }
            }

            if (checkCollisions(prevLoc)) {
                if (returning) {
                    cleanup();
                    return true;
                } else {
                    startReturn();
                }
            }

            displayVisuals(prevLoc);
            return false;
        }

        @Override
        protected void move() {
            Location prevLoc = currentLoc.clone();
            if (returning) {
                Vector moveVec = returnHoming.step(currentLoc);
                if (moveVec == null) {
                    cleanup();
                    return;
                }
                velocity.copy(moveVec);
                currentLoc.add(moveVec);
                returnDistanceTraveled += moveVec.length();
                distanceTraveled += Math.min(moveVec.length(), speed);
            } else {
                super.move();
            }

            updateDisplay(prevLoc);
        }

        private void updateDisplay(Location prevLoc) {
            if (display == null) return;
            display.teleport(AbstractDisplayBuilder.canonical(currentLoc));
            currentRotation += 0.6f;

            Vector heading = currentLoc.toVector().subtract(prevLoc.toVector());
            if (heading.lengthSquared() < 1.0E-12) heading = velocity;
            if (heading.lengthSquared() > 0) {
                Transformation trans = display.getTransformation();
                display.setTransformation(new Transformation(
                        AbstractDisplayBuilder.backTranslation(heading, projVisual.modelBackOffset(), trans.getScale()),
                        AbstractDisplayBuilder.calculateRotation(heading),
                        trans.getScale(),
                        new Quaternionf().rotateY(currentRotation)
                ));
            }
        }

        @Override
        protected void displayVisuals(Location prevLoc) {
            trailTickCounter++;
            if (trailTickCounter >= trailSpacing) {
                trailTickCounter = 0;
                trailVisual.play(currentLoc, velocity, hero.getSkin().getId());
            }
        }

        @Override
        public void cleanup() {
            if (display != null) {
                display.remove();
                display = null;
            }
            super.cleanup();
        }
    }
}