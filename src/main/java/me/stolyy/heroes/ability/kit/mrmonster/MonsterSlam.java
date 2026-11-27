package me.stolyy.heroes.ability.kit.mrmonster;

import me.stolyy.heroes.game.PracticeArena;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.BlockDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
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
    private final Map<UUID, Long> waveHits = new HashMap<>();

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
        this.groundSound = new SoundEffect(Configs.requireSection(this.config, "visuals.ground_sound"));
        this.airSound = new SoundEffect(Configs.requireSection(this.config, "visuals.air_sound"));
        this.hitSound = new SoundEffect(Configs.requireSection(this.config, "visuals.hit_sound"));
        this.waveVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.wave_visual"));
        this.airVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.air_visual"));
    }

    @Override
    protected boolean canUse(Hero hero) {
        return !waveActive;
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
        return WallDetection.rayTraceBlock(feet.clone().add(0, 0.1, 0), new Vector(0, -1, 0),
                groundProximity + 0.1) != null;
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
        double nextFront = Math.min(range, waveFront + waveSpeed);
        double maxFront = PracticeManager.clampPracticeRange(hero, waveOrigin, waveDirection, nextFront);
        if (maxFront <= previousFront) {
            waveActive = false;
            return;
        }
        waveFront = maxFront;

        double width = widthAt(waveFront);
        Location segmentStart = waveOrigin.clone()
                .add(waveDirection.clone().multiply(previousFront))
                .add(0, height / 2.0, 0);
        Hitbox.box(segmentStart, waveDirection, width, height, waveFront - previousFront)
                .exclude(self)
                .excludeTeammates(self)
                .filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))
                .shareHitHistory(waveHits)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    hit(hero, victim)
                            .damage(damage)
                            .knockback(knockback)
                            .verticalKnockback(verticalKnockback)
                            .direction(waveDirection)
                            .execute();
                })
                .check();

        Location frontPoint = waveOrigin.clone().add(waveDirection.clone().multiply(waveFront));
        if (waveFront >= range || waveFront < nextFront) waveActive = false;

        spawnBlockFront(hero, waveFront, width);
        if (PracticeManager.isWithinPracticeBounds(hero, frontPoint)) {
            waveVisual.play(frontPoint);
        }
    }

    private double widthAt(double distance) {
        return minWidth + (distance / range) * (maxWidth - minWidth);
    }

    private void spawnBlockFront(Hero hero, double front, double width) {
        PracticeArena arena = PracticeManager.practiceArenaOf(hero);
        Vector side = waveDirection.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location center = waveOrigin.clone().add(waveDirection.clone().multiply(front));
        for (double w = -width / 2.0; w <= width / 2.0; w += blockSpacing) {
            Location surface = center.clone().add(side.clone().multiply(w));
            if (arena != null && !arena.contains(surface)) continue;
            Location loc = surface.add(0, -1, 0);
            BlockDisplayBuilder.riseAndFall(loc, (float) height, blockDisplayTicks);
        }
    }

    private void airBurst(Hero hero) {
        LivingEntity self = hero.getController().getEntity();
        Location eye = hero.getController().getEyeLocation();
        Vector direction = eye.getDirection();
        double reach = PracticeManager.clampPracticeRange(hero, eye, direction, airRange);
        if (reach <= 0) return;
        double endRadius = airMinRadius + (reach / airRange) * (airRadius - airMinRadius);
        airSound.play(eye, hero.getController());
        airVisual.playAlong(eye, direction, reach, airVisualSpacing, hero.getSkin().getId(), airVisualSpacing);
        Hitbox.cone(eye, direction, airMinRadius, endRadius, reach)
                .exclude(self)
                .excludeTeammates(self)
                .filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))
                .checkWalls(true)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    hit(hero, victim)
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
