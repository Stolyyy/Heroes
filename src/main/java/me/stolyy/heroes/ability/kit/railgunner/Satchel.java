package me.stolyy.heroes.ability.kit.railgunner;

import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class Satchel extends Projectile {
    private final double minVelocity;
    private final double maxVelocity;
    private final long stickyDuration;
    private final double explosionRadius;
    private final int sneakPopDelayTicks;

    private PlantedSatchel plantedSatchel;

    private boolean sneakThrown;
    private int ticksSinceThrow;

    private final SoundEffect popSound;

    public Satchel(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.popSound = new SoundEffect(this.config.getConfigurationSection("visuals.pop_sound"));
        this.minVelocity = config.getDouble("satchel.min_velocity", 0.5);
        this.maxVelocity = config.getDouble("satchel.max_velocity", 2.0);
        this.stickyDuration = (long) (config.getDouble("satchel.duration", 5.0) * 1000);
        this.explosionRadius = config.getDouble("satchel.radius", 4.0);
        this.sneakPopDelayTicks = config.getInt("sneak_pop_delay_ticks");
    }

    @Override
    public boolean execute(Hero hero) {
        if (plantedSatchel != null) {
            sneakThrown = false;
            detonate(hero);
            return true;
        }

        if (!activeProjectiles.isEmpty()) {
            ProjectileInstance proj = activeProjectiles.getFirst();
            sneakThrown = false;
            detonateAt(hero, proj.getLocation());
            proj.cleanup();
            activeProjectiles.clear();
            return true;
        }

        boolean fired = super.execute(hero);
        if (fired) {
            sneakThrown = hero.getController().isSneaking();
            ticksSinceThrow = 0;
        }
        return fired;
    }
    
    @Override
    public boolean canExecute(Hero hero) {
        if (plantedSatchel != null || !activeProjectiles.isEmpty()) {
            return true;
        }
        return super.canExecute(hero);
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        plant(attacker, hitLoc, victim);
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        plant(hero, location, null);
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        detonateAt(hero, location);
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);
        if (plantedSatchel != null) {
            if (plantedSatchel.tick()) {
                detonate(hero);
            }
        }

        if (sneakThrown) {
            ticksSinceThrow++;
            if (ticksSinceThrow >= sneakPopDelayTicks) {
                sneakThrown = false;
                if (!activeProjectiles.isEmpty()) {
                    ProjectileInstance proj = activeProjectiles.getFirst();
                    detonateAt(hero, proj.getLocation());
                    proj.cleanup();
                    activeProjectiles.clear();
                } else if (plantedSatchel != null) {
                    detonate(hero);
                }
            }
        }
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        sneakThrown = false;
        if (plantedSatchel != null) {
            plantedSatchel.cleanup();
            plantedSatchel = null;
        }
    }

    private void plant(Hero hero, Location loc, LivingEntity attached) {
        plantedSatchel = new PlantedSatchel(loc, attached, hero.getSkin().getId());
        hitSound.play(hero.getController().getLocation(), hero.getController());
    }

    private void detonate(Hero hero) {
        if (plantedSatchel == null) return;
        detonateAt(hero, plantedSatchel.getLocation());
        plantedSatchel.cleanup();
        plantedSatchel = null;
    }

    private void detonateAt(Hero hero, Location center) {
        popSound.play(center);
        hitVisual.play(center);
        hitSound.play(hero.getController().getLocation(), hero.getController());

        Hitbox.sphere(center, explosionRadius)
                .onHit((victim) -> {
                    double dist = victim.getLocation().distance(center);
                    double factor = 1.0 - Math.min(1.0, dist / explosionRadius);

                    boolean isEnemy = GameManager.canTarget(hero.getController().getEntity(), victim);

                    if (isEnemy) {
                        Interactions.on(hero.getController().getEntity(), victim).abilityName(getName()).damage(damage).knockback(knockback).fromLocation(center).execute();
                    } else {
                        double velocityStrength = minVelocity + ((maxVelocity - minVelocity) * factor);

                        Vector direction = victim.getEyeLocation().toVector().subtract(center.toVector());
                        if (direction.lengthSquared() < 0.0001) direction = new Vector(0, 1, 0);
                        direction.normalize();
                        
                        Vector finalVel = direction.multiply(velocityStrength);

                        if (finalVel.getY() < 0.2) finalVel.setY(finalVel.getY() + 0.2);
                        
                        victim.setVelocity(finalVel);
                    }
                })
                .check();
    }

    private class PlantedSatchel {
        private final Location location;
        private final LivingEntity attachedEntity;
        private final Vector offset;
        private final long plantTime;
        private final ItemDisplay display;

        public PlantedSatchel(Location location, LivingEntity attachedEntity, String skinId) {
            this.location = location.clone();
            this.attachedEntity = attachedEntity;
            this.plantTime = System.currentTimeMillis();

            if (attachedEntity != null) {
                this.offset = location.toVector().subtract(attachedEntity.getLocation().toVector());
            } else {
                this.offset = null;
            }

            this.display = projVisual.spawnDisplay(location, skinId);
        }

        public boolean tick() {
            if (attachedEntity != null && attachedEntity.isValid()) {
                Location newLoc = attachedEntity.getLocation().add(offset);
                location.setX(newLoc.getX());
                location.setY(newLoc.getY());
                location.setZ(newLoc.getZ());
            }

            if (display != null) {
                display.teleport(AbstractDisplayBuilder.canonical(location));
            }

            return System.currentTimeMillis() - plantTime > stickyDuration;
        }

        public Location getLocation() {
            return location;
        }

        public void cleanup() {
            if (display != null) display.remove();
        }
    }
}