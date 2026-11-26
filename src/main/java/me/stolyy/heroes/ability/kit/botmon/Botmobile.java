package me.stolyy.heroes.ability.kit.botmon;

import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.ItemDisplayBuilder;
import me.stolyy.heroes.vfx.effect.ModelRef;
import me.stolyy.heroes.vfx.effect.RideDisplay;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
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

public class Botmobile extends ActiveAbility {
    private final double moveSpeed;
    private final long hitGracePeriodMs;
    private final double knockbackMultiplier;
    private final double knockbackThreshold;
    private final double damageTakenMult;

    private final ModelRef displayModel;
    private final float displayScale;

    private final SoundEffect windupSound;
    private final SoundEffect loopSound;
    private final int loopSoundInterval;
    private final SoundEffect hitSound;
    private final SoundEffect endSound;
    private final VisualEffect hitVisual;

    private int ticksSinceLoopSound = 0;
    private RideDisplay ride = null;
    private Hitbox hitbox = null;
    private final Map<UUID, Long> hitHistory = new HashMap<>();
    private Vector lastMoveDirection = new Vector(0, 0, 1);

    public Botmobile(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.moveSpeed = Configs.requireDouble(this.config, "move_speed");
        this.hitGracePeriodMs = Configs.requireLong(this.config, "hit_grace_period_ms");
        this.knockbackMultiplier = Configs.requireDouble(this.config, "knockback_multiplier");
        this.knockbackThreshold = Configs.requireDouble(this.config, "knockback_threshold");
        this.damageTakenMult = Configs.requireDouble(this.config, "damage_taken_mult");

        ConfigurationSection displayConfig = Configs.requireSection(this.config, "visuals.display");
        this.displayModel = ModelRef.parse(Configs.requireSection(displayConfig, "model"));
        this.displayScale = (float) Configs.requireDouble(displayConfig, "scale");

        this.loopSoundInterval = Configs.requireInt(this.config, "loop_sound_interval");
        this.windupSound = new SoundEffect(Configs.requireSection(this.config, "visuals.windup_sound"));
        this.loopSound = new SoundEffect(Configs.requireSection(this.config, "visuals.loop_sound"));
        this.hitSound = new SoundEffect(Configs.requireSection(this.config, "visuals.hit_sound"));
        this.endSound = SoundEffect.optional(this.config, "visuals.end_sound");
        this.hitVisual = VisualEffect.optional(this.config, "visuals.hit_visual");
    }

    @Override
    protected void onWindupStart(Hero hero) {
        windupSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected void onActivate(Hero hero) {
        ticksSinceLoopSound = 0;
        hitHistory.clear();

        hero.getAttributes().addKnockbackTakenMultiplier(this, knockbackMultiplier);
        hero.getAttributes().addDamageTakenMultiplier(this, damageTakenMult);

        LivingEntity player = hero.getController().getEntity();
        Location spawnLoc = player.getLocation();
        if (!WallDetection.overlapsCollision(player.getWorld(), player.getBoundingBox().shift(0, 0.5, 0))) {
            spawnLoc = spawnLoc.add(0, 0.5, 0);
        }

        lastMoveDirection = player.getLocation().getDirection().normalize();
        ItemDisplay display = ItemDisplayBuilder.create(spawnLoc)
                .modelData(displayModel, hero.getSkin().getId())
                .scale(displayScale)
                .spawn();
        ride = RideDisplay.mount(hero, display);
        ride.orient(lastMoveDirection);

        this.hitbox = Hitbox.sphere(ride.location().add(0, 1, 0), radius)
                .exclude(player)
                .excludeTeammates(player)
                .shareHitHistory(hitHistory, hitGracePeriodMs)
                .onHit((victim, loc) -> onHitVictim(hero, victim));
    }

    @Override
    protected void onActiveTick(Hero hero) {
        if (ride == null || !ride.isValid()) {
            deactivateNow(hero);
            return;
        }

        LivingEntity player = hero.getController().getEntity();
        Vector lookDir = player.getLocation().getDirection().normalize();
        Vector intendedMove = lookDir.clone().multiply(moveSpeed);

        BoundingBox expanded = player.getBoundingBox().expand(0.15);
        Vector safeMove = WallDetection.clampToWalls(player.getWorld(), expanded, intendedMove);

        Location newLoc = ride.location().add(safeMove);
        if (safeMove.lengthSquared() > 0.01) {
            lastMoveDirection = safeMove.clone().normalize();
            ride.moveTo(newLoc, lastMoveDirection);
        } else {
            ride.moveTo(newLoc);
        }

        unstick(player);

        hitbox.moveTo(ride.location().add(0, 1, 0)).check();

        ticksSinceLoopSound++;
        if (ticksSinceLoopSound >= loopSoundInterval) {
            ticksSinceLoopSound = 0;
            loopSound.play(ride.location(), hero.getController());
        }
    }

    private void onHitVictim(Hero hero, LivingEntity victim) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hitVisual.play(victim.getEyeLocation());
        hit(hero, victim).damage(damage).knockback(knockback).direction(lastMoveDirection).execute();
    }

    @Override
    public void onKnockbackTaken(Hero hero, Entity source, Vector knockback) {
        if (!isActive() || ride == null || !ride.isValid()) return;
        if (knockback.length() < knockbackThreshold) return;

        LivingEntity player = hero.getController().getEntity();
        Vector safeKnockback = WallDetection.clampToWalls(
                player.getWorld(), player.getBoundingBox().expand(0.15), knockback);
        ride.moveTo(ride.location().add(safeKnockback));
    }

    private void unstick(LivingEntity player) {
        if (!WallDetection.isTrapped(player)) return;
        Vector out = WallDetection.escapeOffset(player,
                WallDetection.DEFAULT_ESCAPE_MAX_DISTANCE, WallDetection.DEFAULT_ESCAPE_STEP);
        if (out == null) return;
        ride.moveTo(ride.location().add(out));
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().removeDamageTakenModifier(this);
        hero.getAttributes().removeKnockbackTakenModifier(this);

        if (ride != null) {
            ride.dismountAndRemove(hero);
            ride = null;
        }
        hitbox = null;

        WallDetection.unstick(hero.getController());
        if (hero.getController() != null && hero.getController().getEntity() != null) {
            endSound.play(hero.getController());
        }

        hitHistory.clear();
        ticksSinceLoopSound = 0;
    }
}
