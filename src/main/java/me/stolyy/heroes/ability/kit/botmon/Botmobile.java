package me.stolyy.heroes.ability.kit.botmon;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.ItemDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Botmobile extends BaseAbility {
    private enum State { INACTIVE, WINDUP, ACTIVE }

    private final int windupTicks;
    private final double moveSpeed;
    private final long hitGracePeriodMs;
    private final double knockbackMultiplier;
    private final double knockbackThreshold;
    private final double damageTakenMult;

    private final String[] displayModel;
    private final float displayScale;

    private final SoundEffect windupSound;
    private final SoundEffect loopSound;
    private final int loopSoundInterval;
    private final SoundEffect hitSound;
    private final SoundEffect endSound;
    private final VisualEffect hitVisual;

    private State state = State.INACTIVE;
    private int currentTicks = 0;
    private int ticksSinceLoopSound = 0;
    private ItemDisplay displayEntity = null;
    private final Map<UUID, Long> hitHistory = new HashMap<>();
    private Vector lastMoveDirection = new Vector(0, 0, 1);
    private boolean mitigationApplied;
    private double baseDamageTakenMult;
    private double appliedDamageTakenMult;

    private static final double ESCAPE_MAX_DISTANCE = 3.0;
    private static final double ESCAPE_STEP = 0.1;

    public Botmobile(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.windupTicks = config.getInt("windup_ticks");
        this.moveSpeed = config.getDouble("move_speed");
        this.hitGracePeriodMs = config.getLong("hit_grace_period_ms");
        this.knockbackMultiplier = config.getDouble("knockback_multiplier");
        this.knockbackThreshold = config.getDouble("knockback_threshold");
        this.damageTakenMult = Configs.requireDouble(config, "damage_taken_mult");

        ConfigurationSection displayConfig = config.getConfigurationSection("visuals.display");
        if (displayConfig != null && displayConfig.isConfigurationSection("model")) {
            ConfigurationSection modelConfig = displayConfig.getConfigurationSection("model");
            this.displayModel = new String[] {
                    modelConfig.getString("hero"),
                    modelConfig.getString("texture"),
                    modelConfig.getString("skin", "default")
            };
            this.displayScale = (float) displayConfig.getDouble("scale", 1.0);
        } else {
            this.displayModel = null;
            this.displayScale = 1.0f;
        }

        this.loopSoundInterval = config.getInt("loop_sound_interval");
        this.windupSound = new SoundEffect(this.config.getConfigurationSection("visuals.windup_sound"));
        this.loopSound = new SoundEffect(this.config.getConfigurationSection("visuals.loop_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.endSound = new SoundEffect(this.config.getConfigurationSection("visuals.end_sound"));
        this.hitVisual = new VisualEffect(this.config.getConfigurationSection("visuals.hit_visual"));
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        endAbility(hero);
    }

    @Override
    public void perform(Hero hero) {
        state = State.WINDUP;
        currentTicks = 0;

        windupSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        if (state == State.INACTIVE) return;

        currentTicks++;

        if (state == State.WINDUP) {
            if (currentTicks >= windupTicks) {
                startActive(hero);
            }
            return;
        }

        if (state == State.ACTIVE) {
            if (currentTicks * 50L >= activeDuration) {
                endAbility(hero);
                return;
            }
            tickActive(hero);
        }
    }

    private void startActive(Hero hero) {
        state = State.ACTIVE;
        currentTicks = 0;
        ticksSinceLoopSound = 0;
        hitHistory.clear();

        hero.getAttributes().setKnockbackMultiplier(knockbackMultiplier);
        mitigationApplied = true;
        baseDamageTakenMult = hero.getAttributes().getDamageMultiplier();
        appliedDamageTakenMult = baseDamageTakenMult * damageTakenMult;
        hero.getAttributes().setDamageMultiplier(appliedDamageTakenMult);

        LivingEntity player = hero.getController().getEntity();

        Location spawnLoc = player.getLocation();
        if (!WallDetection.overlapsCollision(player.getWorld(), player.getBoundingBox().shift(0, 0.5, 0))) {
            spawnLoc = spawnLoc.add(0, 0.5, 0);
        }

        if (displayModel != null) {
            displayEntity = ItemDisplayBuilder.create(spawnLoc)
                    .modelData(displayModel[0], displayModel[1], displayModel[2])
                    .scale(displayScale)
                    .spawn();


            if (displayEntity != null) {
                displayEntity.setTeleportDuration(1);
                displayEntity.setInterpolationDuration(1);

                lastMoveDirection = player.getLocation().getDirection().normalize();
                AbstractDisplayBuilder.orient(displayEntity, lastMoveDirection);
                displayEntity.addPassenger(player);
            }
        }
    }

    private void tickActive(Hero hero) {
        if (displayEntity == null || displayEntity.isDead()) {
            endAbility(hero);
            return;
        }

        LivingEntity player = hero.getController().getEntity();
        Vector lookDir = player.getLocation().getDirection().normalize();
        Vector intendedMove = lookDir.clone().multiply(moveSpeed);

        BoundingBox expanded = player.getBoundingBox().expand(0.15);
        Vector safeMove = WallDetection.clampToWalls(player.getWorld(), expanded, intendedMove);

        Location newLoc = displayEntity.getLocation().add(safeMove);
        displayEntity.teleport(AbstractDisplayBuilder.canonical(newLoc));

        if (safeMove.lengthSquared() > 0.01) {
            lastMoveDirection = safeMove.clone().normalize();
            displayEntity.setInterpolationDelay(0);
            AbstractDisplayBuilder.orient(displayEntity, lastMoveDirection);
        }

        unstick(player);

        checkHitbox(hero);

        ticksSinceLoopSound++;
        if (ticksSinceLoopSound >= loopSoundInterval) {
            ticksSinceLoopSound = 0;
            loopSound.play(displayEntity.getLocation(), hero.getController());
        }
    }

    private void checkHitbox(Hero hero) {
        if (displayEntity == null) return;

        long now = System.currentTimeMillis();
        LivingEntity player = hero.getController().getEntity();

        Hitbox hitbox = Hitbox.sphere(displayEntity.getLocation().add(0, 1, 0), radius)
                .exclude(player)
                .filter(e -> {
                    Long lastHit = hitHistory.get(e.getUniqueId());
                    return lastHit == null || (now - lastHit) >= hitGracePeriodMs;
                })
                .onHit((victim, loc) -> {
                    hitHistory.put(victim.getUniqueId(), now);
                    onHitVictim(hero, victim);
                });

        hitbox.excludeTeammates(player);

        hitbox.check();
    }

    private void onHitVictim(Hero hero, LivingEntity victim) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hitVisual.play(victim.getEyeLocation());

        Interactions.on(hero.getController().getEntity(), victim).abilityName(getName()).damage(damage).knockback(knockback).direction(lastMoveDirection).execute();

        hero.triggerAbilityHit(this, victim);
    }

    @Override
    public void onKnockbackTaken(Hero hero, Entity source, Vector knockback) {
        if (state != State.ACTIVE || displayEntity == null) return;
        if (knockback.length() < knockbackThreshold) return;

        LivingEntity player = hero.getController().getEntity();
        Vector safeKnockback = WallDetection.clampToWalls(
                player.getWorld(), player.getBoundingBox().expand(0.15), knockback);
        Location newLoc = displayEntity.getLocation().add(safeKnockback);
        displayEntity.teleport(AbstractDisplayBuilder.canonical(newLoc));
    }

    private void unstick(LivingEntity player) {
        if (!WallDetection.isTrapped(player)) return;
        Vector out = WallDetection.escapeOffset(player, ESCAPE_MAX_DISTANCE, ESCAPE_STEP);
        if (out == null) return;
        displayEntity.teleport(AbstractDisplayBuilder.canonical(displayEntity.getLocation().add(out)));
    }

    private void endAbility(Hero hero) {
        if (state == State.INACTIVE) return;

        state = State.INACTIVE;

        if (mitigationApplied) {
            mitigationApplied = false;
            if (hero.getAttributes().getDamageMultiplier() == appliedDamageTakenMult) {
                hero.getAttributes().setDamageMultiplier(baseDamageTakenMult);
            }
        }
        hero.getAttributes().resetKnockbackMultiplier();

        LivingEntity player = hero.getController().getEntity();

        if (displayEntity != null) {
            displayEntity.eject();
            displayEntity.remove();
            displayEntity = null;
        }


        if (WallDetection.isTrapped(player)) {
            Vector out = WallDetection.escapeOffset(player, ESCAPE_MAX_DISTANCE, ESCAPE_STEP);
            if (out != null) player.teleport(player.getLocation().add(out));
        }

        endSound.play(player.getLocation(), hero.getController());

        hitHistory.clear();
        currentTicks = 0;
        ticksSinceLoopSound = 0;
    }

    @Override
    public void onExpire(Hero hero, org.bukkit.Location location) {
        endAbility(hero);
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }
}
