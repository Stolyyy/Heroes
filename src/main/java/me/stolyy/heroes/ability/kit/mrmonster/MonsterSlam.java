package me.stolyy.heroes.ability.kit.mrmonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.BlockDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MonsterSlam extends BaseAbility {
    private final double groundProximity;

    private final double range;
    private final double waveSpeed;
    private final double minWidth;
    private final double maxWidth;
    private final double height;
    private final double verticalKnockback;

    private final double airRange;
    private final double airMinRadius;
    private final double airRadius;
    private final double airDamage;
    private final double airKnockback;

    private final double airKbAngle;

    private final int blockDisplayTicks;
    private final double blockSpacing;
    private final SoundEffect groundSound;
    private final SoundEffect airSound;
    private final SoundEffect hitSound;
    private final VisualEffect waveVisual;
    private final VisualEffect airVisual;
    private final double airVisualSpacing;

    private boolean waveActive;
    private double waveFront;
    private Location waveOrigin;
    private Vector waveDirection;
    private final Set<UUID> waveHits = new HashSet<>();

    public MonsterSlam(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.groundProximity = Configs.requireDouble(this.config, "ground_proximity");
        this.range = Configs.requireDouble(this.config, "range");
        this.waveSpeed = Configs.requireDouble(this.config, "wave_speed");
        this.minWidth = Configs.requireDouble(this.config, "min_width");
        this.maxWidth = Configs.requireDouble(this.config, "max_width");
        this.height = Configs.requireDouble(this.config, "height");
        this.verticalKnockback = Configs.requireDouble(this.config, "vertical_knockback");
        this.airRange = Configs.requireDouble(this.config, "air_range");
        this.airMinRadius = Configs.requireDouble(this.config, "air_min_radius");
        this.airRadius = Configs.requireDouble(this.config, "air_radius");
        this.airDamage = Configs.requireDouble(this.config, "air_damage");
        this.airKnockback = Configs.requireDouble(this.config, "air_knockback");
        this.airKbAngle = Configs.requireDouble(this.config, "air_kb_angle");
        this.blockDisplayTicks = Configs.requireInt(this.config, "visuals.block_display_ticks");
        this.blockSpacing = Configs.requireDouble(this.config, "visuals.block_spacing");
        this.airVisualSpacing = Configs.requireDouble(this.config, "visuals.air_visual_spacing");
        this.groundSound = new SoundEffect(this.config.getConfigurationSection("visuals.ground_sound"));
        this.airSound = new SoundEffect(this.config.getConfigurationSection("visuals.air_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.waveVisual = new VisualEffect(this.config.getConfigurationSection("visuals.wave_visual"));
        this.airVisual = new VisualEffect(this.config.getConfigurationSection("visuals.air_visual"));
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (waveActive) return false;
        return super.canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        Location feet = hero.getController().getLocation();
        if (nearGround(feet)) {
            startWave(hero, feet);
        } else {
            airBurst(hero);
        }
    }

    private boolean nearGround(Location feet) {
        return feet.getWorld().rayTraceBlocks(feet.clone().add(0, 0.1, 0), new Vector(0, -1, 0),
                groundProximity + 0.1, FluidCollisionMode.NEVER, true) != null;
    }

    private void startWave(Hero hero, Location feet) {
        groundSound.play(feet, hero.getController());
        waveActive = true;
        waveFront = 0;
        waveHits.clear();

        Vector look = feet.getDirection();
        look.setY(0);
        if (look.lengthSquared() < 1.0E-6) look = new Vector(1, 0, 0);
        waveDirection = look.normalize();
        waveOrigin = feet.clone();
    }

    @Override
    protected void onTick(Hero hero) {
        if (!waveActive) return;
        LivingEntity self = hero.getController() != null ? hero.getController().getEntity() : null;
        if (self == null) {
            waveActive = false;
            return;
        }

        double previousFront = waveFront;
        waveFront = Math.min(range, waveFront + waveSpeed);

        double width = widthAt(waveFront);
        Location segmentStart = waveOrigin.clone()
                .add(waveDirection.clone().multiply(previousFront))
                .add(0, height / 2.0, 0);
        Hitbox.box(segmentStart, waveDirection, width, height, waveFront - previousFront)
                .exclude(self)
                .excludeTeammates(self)
                .filter(victim -> !waveHits.contains(victim.getUniqueId()))
                .onHit(victim -> {
                    waveHits.add(victim.getUniqueId());
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    hero.triggerAbilityHit(this, victim);
                    Interactions.on(self, victim)
                            .abilityName(getName())
                            .damage(damage)
                            .knockback(knockback)
                            .verticalKnockback(verticalKnockback)
                            .direction(waveDirection)
                            .execute();
                })
                .check();

        if (waveFront >= range) waveActive = false;

        spawnBlockFront(waveFront, width);
        waveVisual.play(waveOrigin.clone().add(waveDirection.clone().multiply(waveFront)));
    }

    private double widthAt(double distance) {
        return minWidth + (distance / range) * (maxWidth - minWidth);
    }

    private void spawnBlockFront(double front, double width) {
        Vector side = waveDirection.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location center = waveOrigin.clone().add(waveDirection.clone().multiply(front));
        for (double w = -width / 2.0; w <= width / 2.0; w += blockSpacing) {
            Location loc = center.clone().add(side.clone().multiply(w)).add(0, -1, 0);
            Block block = loc.getBlock();
            if (block.getType().isAir()) block = loc.clone().add(0, -1, 0).getBlock();
            if (block.getType().isAir() || !block.getType().isSolid()) continue;
            org.bukkit.entity.BlockDisplay display = BlockDisplayBuilder
                    .create(block.getLocation().add(0.5, 0, 0.5))
                    .block(block.getType())
                    .duration(blockDisplayTicks + 2)
                    .brightness(15, 15)
                    .spawn();
            if (display != null) animateRiseAndFall(display);
        }
    }

    private void animateRiseAndFall(org.bukkit.entity.BlockDisplay display) {
        int riseTicks = Math.max(1, blockDisplayTicks / 2);
        int fallTicks = Math.max(1, blockDisplayTicks - riseTicks);
        org.bukkit.Bukkit.getScheduler().runTaskLater(me.stolyy.heroes.Heroes.getInstance(), () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(riseTicks);
            org.bukkit.util.Transformation t = display.getTransformation();
            display.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, (float) height, 0),
                    t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        }, 1L);
        org.bukkit.Bukkit.getScheduler().runTaskLater(me.stolyy.heroes.Heroes.getInstance(), () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(fallTicks);
            org.bukkit.util.Transformation t = display.getTransformation();
            display.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        }, 1L + riseTicks);
    }

    private void airBurst(Hero hero) {
        LivingEntity self = hero.getController().getEntity();
        Location eye = hero.getController().getEyeLocation();
        Vector direction = eye.getDirection();
        airSound.play(eye, hero.getController());
        for (double d = airVisualSpacing; d <= airRange; d += airVisualSpacing) {
            airVisual.play(eye.clone().add(direction.clone().multiply(d)));
        }
        Hitbox.cone(eye, direction, airMinRadius, airRadius, airRange)
                .exclude(self)
                .excludeTeammates(self)
                .checkWalls(true)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    hero.triggerAbilityHit(this, victim);
                    Interactions.on(self, victim)
                            .abilityName(getName())
                            .damage(airDamage)
                            .knockback(airKnockback)
                            .pitch(airKbAngle)
                            .direction(direction)
                            .execute();
                })
                .check();
    }
    
    @Override
    public boolean isIndependent() {
        return true;
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        waveActive = false;
        waveHits.clear();
    }
}
