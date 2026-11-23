package me.stolyy.heroes.ability.kit.blud;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.physics.Homing;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class BludPrimary extends BaseAbility {
    public enum Mode { DASH, BOOMERANG, TRACKING }

    private final Dash dashMode;
    private final BoomerangMode boomerangMode;
    private final Hitscan trackingMode;

    private Mode mode = Mode.DASH;

    public BludPrimary(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.dashMode = new Dash(gatedSub("dash"), skin) {};
        this.boomerangMode = new BoomerangMode(gatedSub("boomerang"), skin);
        this.trackingMode = new Hitscan(gatedSub("tracking"), skin) {};
    }

    private ConfigurationSection gatedSub(String key) {
        ConfigurationSection sub = this.config.getConfigurationSection(key);
        sub.set("type", "SECONDARY");
        return sub;
    }

    public Mode getMode() {
        return mode;
    }

    private BaseAbility activeMode() {
        return switch (mode) {
            case DASH -> dashMode;
            case BOOMERANG -> boomerangMode;
            case TRACKING -> trackingMode;
        };
    }

    @Override
    public boolean execute(Hero hero) {
        return activeMode().execute(hero);
    }

    @Override
    public void perform(Hero hero) {
    }

    void advanceMode(Hero hero) {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        announce(hero);
    }

    void retreatMode(Hero hero) {
        mode = Mode.values()[(mode.ordinal() + Mode.values().length - 1) % Mode.values().length];
        announce(hero);
    }

    private void announce(Hero hero) {
        hero.getController().sendActionBar(Component.text("Mode: " + mode.name(), NamedTextColor.RED));
    }

    @Override
    protected void onTick(Hero hero) {
        dashMode.tick(hero);
        boomerangMode.tick(hero);
        trackingMode.tick(hero);
    }

    @Override
    public boolean isIndependent() {
        return true;
    }

    @Override
    public void onOwnerLost(Hero hero) {
        boomerangMode.onOwnerLost(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        dashMode.onDeath(hero);
        boomerangMode.onDeath(hero);
        trackingMode.onDeath(hero);
    }

    private static class BoomerangMode extends Projectile {
        BoomerangMode(ConfigurationSection config, HeroSkin skin) {
            super(config, skin);
        }

        @Override
        protected void fireSingleShot(Hero hero) {
            fireSound.play(hero.getController().getLocation(), hero.getController());
            activeProjectiles.add(new BoomerangInstance(hero, getStartLocation(hero)));
        }

        private class BoomerangInstance extends ProjectileInstance {
            private boolean returning = false;
            private double returnDistance = 0;
            private final Homing returnHoming;

            BoomerangInstance(Hero hero, Location start) {
                super(hero, start);
                this.returnHoming = new Homing(hero.getController().getEntity(), speed, () -> null);
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
                    if (currentLoc.distanceSquared(hero.getController().getLocation()) < 1.5 * 1.5
                            || returnDistance > range * 2) {
                        cleanup();
                        return true;
                    }
                }

                if (checkCollisions(prevLoc)) {
                    if (returning) {
                        finish(prevLoc);
                        return true;
                    }
                    startReturn();
                }

                displayVisuals(prevLoc);
                return false;
            }

            @Override
            protected void move() {
                if (returning) {
                    Vector step = returnHoming.step(currentLoc);
                    if (step == null) {
                        cleanup();
                        return;
                    }
                    velocity.copy(step);
                    currentLoc.add(step);
                    returnDistance += step.length();
                    distanceTraveled += Math.min(step.length(), speed);
                } else {
                    super.move();
                }
            }

            private void startReturn() {
                returning = true;
                hitbox.clearHitHistory();
            }
        }
    }
}
