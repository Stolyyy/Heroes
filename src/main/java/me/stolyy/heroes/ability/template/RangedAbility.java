package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.VectorUtil;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared base for ranged attack templates (Hitscan, Projectile).
 * Owns common config parsing, burst firing, damage dropoff, knockback handling,
 * spread, persistent hit history, and hit/wall/expire hooks. Subclasses implement the
 * firing mechanism via {@link #fireSingleShot(Hero)}.
 */
public abstract class RangedAbility extends BaseAbility {
    public enum KBType { DIRECTION, HIT_LOCATION, ATTACKER, PULL }
    public enum ImpactCause { HIT, WALL, EXPIRE }

    public record ShotParams(@Nullable Double damage, @Nullable Double knockback, double speed,
                             SoundEffect fireSound, VisualEffect trailVisual, VisualEffect hitVisual) {}

    protected double range;
    protected boolean piercesPlayers;
    protected boolean piercesWalls;
    protected KBType kbType;

    protected double minDamage;
    protected double dropoffDistance;
    protected double lastHitDistance;

    protected double spreadDeg;
    protected boolean spreadAlternate;

    protected int burstCount;
    protected int burstDelay;
    protected long burstStartDelayMs;
    protected boolean burstUntilInactive;
    protected long hitIntervalMs;

    private boolean burstActive;
    private int activeBurstTotal;
    private int burstShotsFired;
    private int burstElapsedTicks;
    private int currentShotIndex;

    protected final Map<UUID, Long> persistentHitHistory = new HashMap<>();
    private @Nullable ShotParams activeShot;

    /**
     * For Hitscan: distance step (blocks) between trail particle emissions along the shot line.
     * For Projectile (tick-mode): tick count between emissions; fractional values round up via the
     * {@code trailTickCounter >= trailSpacing} comparison.
     */
    protected double trailSpacing;
    /** When > 0, projectiles emit one trail every this-many blocks travelled instead of every {@link #trailSpacing} ticks. */
    protected double trailSpacingDistance;
    /** When true, projectile trail emissions live as long as the projectile lives, then despawn together. */
    protected boolean trailPersists;

    protected SoundEffect fireSound, hitSound, wallSound;
    protected VisualEffect trailVisual, hitVisual, wallVisual;
    protected @Nullable Aoe aoe;

    /**
     * Set by subclasses before invoking {@link #onHit(Hero, LivingEntity, Location)}
     * so KB direction modes can use the shot's travel direction.
     */
    protected Vector currentDirection;

    public RangedAbility(ConfigurationSection config, HeroSkin skin) {
        this(config, skin, null);
    }

    protected RangedAbility(ConfigurationSection config, HeroSkin skin, AbilityType explicitType) {
        super(config, skin, explicitType);
        this.range = Configs.orTemplate(this.config, "range", "ranged.range");
        this.piercesPlayers = Configs.orTemplateBoolean(this.config, "pierces_players", "ranged.pierces_players");
        this.piercesWalls = Configs.orTemplateBoolean(this.config, "pierces_walls", "ranged.pierces_walls");
        this.kbType = KBType.valueOf(Configs.orTemplateString(this.config, "kb_type", "ranged.kb_type").toUpperCase());

        this.minDamage = Configs.orTemplate(this.config, "min_damage", "ranged.min_damage");
        this.dropoffDistance = Configs.orTemplate(this.config, "dropoff_distance", "ranged.dropoff_distance");
        this.spreadDeg = Configs.orTemplate(this.config, "spread_deg", "ranged.spread_deg");
        this.spreadAlternate = Configs.orTemplateBoolean(this.config, "spread_alternate", "ranged.spread_alternate");
        this.burstCount = Configs.orTemplateInt(this.config, "burst_count", "ranged.burst_count");
        this.burstDelay = Configs.orTemplateInt(this.config, "burst_delay_ms", "ranged.burst_delay_ms");
        this.burstStartDelayMs = Configs.orTemplateLong(this.config, "burst_start_delay_ms", "ranged.burst_start_delay_ms");
        this.burstUntilInactive = Configs.orTemplateBoolean(this.config, "burst_until_inactive", "ranged.burst_until_inactive");
        this.hitIntervalMs = Configs.orTemplateInt(this.config, "hit_interval_ticks", "ranged.hit_interval_ticks") * Heroes.MS_PER_TICK;

        this.trailSpacing = Configs.orTemplate(this.config, "visuals.trail_spacing", "ranged.trail_spacing");
        if (!Double.isFinite(this.trailSpacing) || this.trailSpacing <= 0) {
            throw new IllegalArgumentException("RangedAbility visuals.trail_spacing must be > 0, got " + this.trailSpacing);
        }
        this.trailSpacingDistance = Configs.orTemplate(this.config, "visuals.trail_spacing_distance", "ranged.trail_spacing_distance");
        this.trailPersists = Configs.orTemplateBoolean(this.config, "visuals.trail_persists", "ranged.trail_persists");

        this.fireSound = SoundEffect.optional(this.config, "visuals.fire_sound");
        this.hitSound = SoundEffect.optional(this.config, "visuals.hit_sound");
        this.wallSound = SoundEffect.optional(this.config, "visuals.wall_sound");

        this.trailVisual = VisualEffect.optional(this.config, "visuals.trail_visual");
        this.hitVisual = VisualEffect.optional(this.config, "visuals.hit_visual");
        this.wallVisual = VisualEffect.optional(this.config, "visuals.wall_visual");

        this.aoe = this.config.contains("aoe") ? new Aoe(Configs.requireSection(this.config, "aoe"), this) : null;
    }

    @Override
    public void perform(Hero hero) {
        persistentHitHistory.clear();
        activeBurstTotal = burstCountFor(hero);
        if (!burstUntilInactive && activeBurstTotal <= 0) return;
        burstElapsedTicks = 0;
        if (burstStartDelayMs > 0) {
            burstShotsFired = 0;
            currentShotIndex = 0;
            burstActive = true;
        } else {
            currentShotIndex = 0;
            onBurstShot(hero, 0);
            fireSingleShot(hero);
            burstShotsFired = 1;
            burstActive = burstUntilInactive || activeBurstTotal > 1;
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        // Firing is character-bound: a death cancels any in-progress burst so a dormant
        // (eliminated) hero can't keep shooting posthumously via the independent tick path.
        burstActive = false;
        persistentHitHistory.clear();
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        burstActive = false;
        persistentHitHistory.clear();
    }

    @Override
    protected final void onTick(Hero hero) {
        if (burstActive) {
            boolean finished = burstUntilInactive ? !cooldown.isInUse() : burstShotsFired >= activeBurstTotal;
            if (finished) {
                burstActive = false;
                onBurstEnd(hero);
            } else {
                burstElapsedTicks++;
                long thresholdMs = (burstShotsFired == 0 && burstStartDelayMs > 0)
                        ? burstStartDelayMs : burstDelayMsFor(hero);
                if (burstElapsedTicks * Heroes.MS_PER_TICK >= thresholdMs) {
                    if (ammo != null && !ammo.tryConsume()) {
                        burstActive = false;
                        onBurstEnd(hero);
                    } else {
                        currentShotIndex = burstShotsFired;
                        onBurstShot(hero, currentShotIndex);
                        fireSingleShot(hero);
                        burstShotsFired++;
                        burstElapsedTicks = 0;
                    }
                }
            }
        }
        onRangedTick(hero);
    }

    protected void withActiveShot(@Nullable ShotParams shot, Runnable action) {
        ShotParams previous = this.activeShot;
        this.activeShot = shot;
        try {
            action.run();
        } finally {
            this.activeShot = previous;
        }
    }

    protected @Nullable ShotParams activeShot() { return activeShot; }

    protected ShotParams shotParamsFor(Hero hero) {
        return new ShotParams(null, null, 0.0, fireSound, trailVisual, hitVisual);
    }

    protected int currentShotIndex() { return currentShotIndex; }

    protected void onRangedTick(Hero hero) {}
    protected int burstCountFor(Hero hero) { return burstCount; }
    protected long burstDelayMsFor(Hero hero) { return burstDelay; }
    protected void onBurstShot(Hero hero, int shotIndex) {}
    protected void onBurstEnd(Hero hero) {}

    /** Subclass-specific firing mechanism. Called by {@link #perform(Hero)} and burst tick. */
    protected abstract void fireSingleShot(Hero hero);

    @Override
    public boolean piercesWalls() { return piercesWalls; }

    protected Location getStartLocation(Hero hero) {
        return hero.getController().getEyeLocation();
    }

    protected Vector getDirection(Location location) {
        Vector dir = location.getDirection();
        if (spreadDeg <= 0) return dir;
        if (spreadAlternate) {
            double angleRad = ThreadLocalRandom.current().nextDouble() * Math.toRadians(spreadDeg);
            if (currentShotIndex % 2 != 0) angleRad = -angleRad;
            return dir.clone().rotateAroundAxis(new Vector(0, 1, 0), angleRad).normalize();
        }
        return VectorUtil.randomSpread(dir, spreadDeg);
    }

    protected void detonateAoe(Hero attacker, Location location, @Nullable LivingEntity excludeVictim) {
        detonateAoe(attacker, location, null, excludeVictim);
    }

    /**
     * @param trajectory the shot's travel direction at impact. The Aoe uses it to back a center that
     *                   landed inside geometry out along the path the shot arrived on.
     */
    protected void detonateAoe(Hero attacker, Location location, @Nullable Vector trajectory,
                               @Nullable LivingEntity excludeVictim) {
        if (aoe != null) {
            double fallbackDmg = (activeShot != null && activeShot.damage() != null) ? activeShot.damage() : damage;
            double fallbackKb = (activeShot != null && activeShot.knockback() != null) ? activeShot.knockback() : knockback;
            aoe.detonate(location, trajectory, attacker, excludeVictim, fallbackDmg, fallbackKb);
        }
    }

    @Override
    protected double calcDamage(Hero hero, LivingEntity victim) {
        double base = (activeShot != null && activeShot.damage() != null) ? activeShot.damage() : damage;
        if (dropoffDistance <= 0) return base;
        return Math.max(minDamage, base - (lastHitDistance / dropoffDistance));
    }

    @Override
    protected double calcKnockback(Hero hero, LivingEntity victim) {
        return (activeShot != null && activeShot.knockback() != null) ? activeShot.knockback() : knockback;
    }

    protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
        Interactions.InteractionBuilder interaction = hit(attacker, victim);
        double dmg = calcDamage(attacker, victim);
        double kb = calcKnockback(attacker, victim);
        Vector dir = currentDirection != null ? currentDirection : hitLoc.getDirection();
        switch (kbType) {
            case DIRECTION -> interaction.damage(dmg).knockback(kb).direction(dir).execute();
            case HIT_LOCATION -> interaction.damage(dmg).knockback(kb).fromLocation(hitLoc).execute();
            case ATTACKER -> interaction.damage(dmg).knockback(kb).execute();
            case PULL -> interaction.damage(dmg).knockback(kb)
                    .direction(dir.clone().multiply(-1)).processDirection(false).execute();
        }
    }

    /**
     * The sound for a direct hit. Subclasses override to SUBSTITUTE a different sound per hit
     * (e.g. Railgunner's headshot) rather than layering a second one on top.
     */
    protected SoundEffect hitSoundFor(Hero attacker, LivingEntity victim, Location hitLoc) {
        return hitSound;
    }

    protected void playHitEffects(Hero attacker, LivingEntity victim, Location hitLoc) {
        hitSoundFor(attacker, victim, hitLoc).play(victim.getEyeLocation(), attacker.getController());
        (activeShot != null ? activeShot.hitVisual() : hitVisual).play(victim.getEyeLocation());
    }

    protected void afterHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        // A direct hit is the one path that can bury the blast center: hitLoc comes from a ray-trace
        // against the victim's AABB grown by the projectile radius, so a fat shot resolves it well
        // past the victim. currentDirection is set by the subclass immediately before this call.
        detonateAoe(attacker, hitLoc, currentDirection, victim);
    }

    protected void onBeforeImpact(Hero hero, Location location, @Nullable LivingEntity victim, ImpactCause cause) {}
    protected void onImpact(Hero hero, Location location, @Nullable LivingEntity victim, ImpactCause cause) {}

    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        onBeforeImpact(attacker, hitLoc, victim, ImpactCause.HIT);
        playHitEffects(attacker, victim, hitLoc);
        applyInteraction(attacker, victim, hitLoc);
        afterHit(attacker, victim, hitLoc);
        onImpact(attacker, hitLoc, victim, ImpactCause.HIT);
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        onBeforeImpact(hero, location, null, ImpactCause.WALL);
        wallSound.play(location, hero.getController());
        wallVisual.play(location);
        // Projectile writes currentDirection from the segment it is resolving, immediately before the
        // hitbox fires this, so the blast backs out along the path the shot actually arrived on.
        // Wall locations are already backed off the struck face by Hitbox.WALL_HIT_BACKOFF, so
        // Aoe.resolveCenter normally short-circuits — but WallDetection.isInsideSolid is a whole-BLOCK
        // test, so a landing on a slab or a stair reports a point that is geometrically in air inside
        // a block that reads solid, and that path was retreating toward the shooter's eye instead of
        // back up the arc. Hitscan does not write currentDirection on its wall path, so there the
        // value can be a previous shot's heading — the same rough direction, and only ever consulted
        // in that slab/stair case.
        detonateAoe(hero, location, currentDirection, null);
        onImpact(hero, location, null, ImpactCause.WALL);
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        onBeforeImpact(hero, location, null, ImpactCause.EXPIRE);
        detonateAoe(hero, location, null);
        onImpact(hero, location, null, ImpactCause.EXPIRE);
    }
}
