package me.stolyy.heroes.ability.kit.tinman;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Homing;
import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.*;

public class NukeSeekingMissiles extends BaseAbility {
    private final int missilesPerEnemy;
    private final double launchHeight;
    private final double speed;
    private final double aoeRadius;
    private final double damage;
    private final double knockback;
    private final int maxTicks;
    private final int spawnDelayTicks;
    private final int maxMissiles;

    private final VisualEffect missileModel;
    private final VisualEffect trailVisual;
    private final SoundEffect activationSound;
    private final SoundEffect hitSound;
    private final SoundEffect explosionSound;
    private final VisualEffect explosionVisual;

    private record PendingMissile(Hero hero, LivingEntity target, int spawnTick) {}

    private final List<PendingMissile> pendingMissiles = new ArrayList<>();
    private final List<MissileInstance> activeMissiles = new ArrayList<>();
    private int tickCount = 0;

    public NukeSeekingMissiles(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.missilesPerEnemy = config.getInt("missiles_per_enemy", 3);
        this.launchHeight = config.getDouble("launch_height", 10.0);
        this.speed = config.getDouble("speed", 1.2);
        this.aoeRadius = config.getDouble("aoe_radius", 4.0);
        this.damage = config.getDouble("damage", 10.0);
        this.knockback = config.getDouble("knockback", 2.5);
        this.maxTicks = config.getInt("max_ticks", 100);
        this.spawnDelayTicks = config.getInt("spawn_delay_ticks", 2);
        this.maxMissiles = Configs.requireInt(config, "max_missiles");

        this.missileModel = new VisualEffect(this.config.getConfigurationSection("visuals.missile_model"));
        this.trailVisual = new VisualEffect(this.config.getConfigurationSection("visuals.trail_visual"));
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.explosionSound = new SoundEffect(this.config.getConfigurationSection("visuals.explosion_sound"));
        this.explosionVisual = new VisualEffect(this.config.getConfigurationSection("visuals.explosion_visual"));
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());

        LivingEntity self = hero.getController().getEntity();
        List<LivingEntity> targets = GameManager.getEnemies(self).stream()
                .filter(e -> GameManager.canTarget(self, e))
                .filter(e -> !(HeroManager.getHero(e) instanceof MinionHero))
                .toList();
        if (targets.isEmpty()) return;

        int totalMissiles = Math.min(targets.size() * missilesPerEnemy, maxMissiles);

        for (int i = 0; i < totalMissiles; i++) {
            LivingEntity target = targets.get(i % targets.size());
            pendingMissiles.add(new PendingMissile(hero, target, tickCount + i * spawnDelayTicks));
        }
    }

    @Override
    public void onOwnerLost(Hero hero) {
        pendingMissiles.clear();
        for (MissileInstance missile : activeMissiles) missile.removeDisplay();
        activeMissiles.clear();
    }

    @Override
    protected void onTick(Hero hero) {
        tickCount++;

        Iterator<PendingMissile> it = pendingMissiles.iterator();
        while (it.hasNext()) {
            PendingMissile pending = it.next();
            if (tickCount >= pending.spawnTick()) {
                Location spawnLoc = pending.hero().getController().getEyeLocation();
                activeMissiles.add(new MissileInstance(pending.hero(), pending.target(), spawnLoc));
                it.remove();
            }
        }

        activeMissiles.removeIf(MissileInstance::tick);
    }

    private class MissileInstance {
        private final Hero shooter;
        private final Location loc;
        private final Vector velocity;
        private final Homing homing;
        private boolean seeking = false;
        private int elapsed = 0;
        private ItemDisplay display;
        private boolean exploded = false;

        public MissileInstance(Hero shooter, LivingEntity target, Location start) {
            this.shooter = shooter;
            this.loc = start;
            this.velocity = new Vector(0, 1, 0).multiply(speed);
            this.homing = new Homing(target, speed, this::findClosestEnemy, this::canTarget);

            this.display = missileModel.spawnDisplay(loc, shooter.getSkin().getId());
            if (this.display != null) {
                this.display.setInterpolationDelay(0);
                this.display.setTeleportDuration(1);
                AbstractDisplayBuilder.orient(display, velocity, missileModel.modelBackOffset());
            }
        }

        private void removeDisplay() {
            if (display != null) {
                display.remove();
                display = null;
            }
        }

        private boolean canTarget(LivingEntity victim) {
            return GameManager.canTarget(shooter.getController().getEntity(), victim);
        }

        private LivingEntity findClosestEnemy() {
            LivingEntity self = shooter.getController().getEntity();
            return GameManager.getEnemies(self).stream()
                    .filter(e -> GameManager.canTarget(self, e))
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(loc)))
                    .orElse(null);
        }

        public boolean tick() {
            elapsed++;

            if (elapsed > maxTicks) {
                explode();
                return true;
            }

            Vector homingVel = homing.step(loc);
            if (homingVel == null) {
                explode();
                return true;
            }

            if (seeking) {
                velocity.copy(homingVel);
            }

            Location prevLoc = loc.clone();
            loc.add(velocity);

            if (sweptCollision(prevLoc)) return true;

            if (display != null) {
                display.teleport(AbstractDisplayBuilder.canonical(loc));
                AbstractDisplayBuilder.orient(display, velocity, missileModel.modelBackOffset());
            }

            trailVisual.play(loc, shooter.getSkin().getId());

            if (!seeking && elapsed * speed >= launchHeight) {
                seeking = true;
            }

            return false;
        }

        private boolean sweptCollision(Location from) {
            Vector dir = velocity.clone();
            double dist = dir.length();
            if (dist < 0.001) return false;

            LivingEntity self = shooter.getController().getEntity();
            Hitbox hb = Hitbox.cylinder(from, dir, 0.5, dist)
                    .exclude(self)
                    .excludeTeammates(self)
                    .checkWalls(true);

            Set<LivingEntity> hits = hb.get();
            if (!hits.isEmpty() || hb.getWallHitLocation() != null) {
                explode();
                return true;
            }
            return false;
        }

        private void explode() {
            if (exploded) return;
            exploded = true;

            if (display != null) {
                display.remove();
                display = null;
            }

            explosionSound.play(loc);
            explosionVisual.play(loc);

            LivingEntity self = shooter.getController().getEntity();
            Hitbox aoe = Hitbox.sphere(loc, aoeRadius)
                    .exclude(self)
                    .pierce(true)
                    .onHit((victim) -> {
                        hitSound.play(victim.getEyeLocation(), shooter.getController());
                        Interactions.on(self, victim).abilityName(NukeSeekingMissiles.this.getName()).damage(damage).knockback(knockback).execute();
                    });

            aoe.excludeTeammates(self);
            aoe.check();
        }
    }
}
