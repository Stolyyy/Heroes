package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for ranged attack templates (Hitscan, Projectile).
 * Owns common config parsing, burst firing, damage dropoff, knockback handling,
 * and onHit/onHitWall/onExpire plumbing. Subclasses implement the specific
 * firing mechanism via {@link #fireSingleShot(Hero)}.
 */
public abstract class RangedAbility extends BaseAbility {
    public enum KBType { DIRECTION, HIT_LOCATION, ATTACKER }

    protected double range;
    protected boolean piercesPlayers;
    protected boolean piercesWalls;
    protected KBType kbType;

    protected double minDamage;
    protected double dropoffDistance;
    protected double lastHitDistance;

    protected int burstCount;
    protected int burstDelay;
    private int burstShotsFired;
    private int burstElapsed;

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
     * so KB direction mode {@link KBType#DIRECTION} can use the shot's travel direction.
     */
    protected Vector currentDirection;

    public RangedAbility(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.range = this.config.getDouble("range", 50.0);
        this.piercesPlayers = this.config.getBoolean("pierces_players", false);
        this.piercesWalls = this.config.getBoolean("pierces_walls", false);
        this.kbType = KBType.valueOf(this.config.getString("kb_type", "DIRECTION").toUpperCase());

        this.minDamage = this.config.getDouble("min_damage", 0);
        this.dropoffDistance = this.config.getDouble("dropoff_distance", 0);
        this.burstCount = this.config.getInt("burst_count", 1);
        this.burstDelay = this.config.getInt("burst_delay", 0);

        this.trailSpacing = this.config.getDouble("visuals.trail_spacing", 1.0);
        this.trailSpacingDistance = this.config.getDouble("visuals.trail_spacing_distance", 0.0);
        this.trailPersists = this.config.getBoolean("visuals.trail_persists", false);

        this.fireSound = new SoundEffect(this.config.getConfigurationSection("visuals.fire_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.wallSound = new SoundEffect(this.config.getConfigurationSection("visuals.wall_sound"));

        this.trailVisual = new VisualEffect(this.config.getConfigurationSection("visuals.trail_visual"));
        this.hitVisual = new VisualEffect(this.config.getConfigurationSection("visuals.hit_visual"));
        this.wallVisual = new VisualEffect(this.config.getConfigurationSection("visuals.wall_visual"));

        ConfigurationSection aoeSection = this.config.getConfigurationSection("aoe");
        this.aoe = aoeSection != null ? new Aoe(aoeSection, this) : null;
    }

    @Override
    public void perform(Hero hero) {
        fireSingleShot(hero);
        burstShotsFired = 1;
        burstElapsed = 0;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        // Firing is character-bound: a death cancels any in-progress burst so a dormant
        // (eliminated) hero can't keep shooting posthumously via the independent tick path.
        burstShotsFired = burstCount;
    }

    @Override
    protected void onTick(Hero hero) {
        if (burstShotsFired > 0 && burstShotsFired < burstCount) {
            burstElapsed++;
            if (burstElapsed * 50L >= burstDelay) {
                if (ammo != null && !ammo.tryConsume()) {
                    burstShotsFired = burstCount;
                    return;
                }
                fireSingleShot(hero);
                burstShotsFired++;
                burstElapsed = 0;
            }
        }
    }

    /** Subclass-specific firing mechanism. Called by {@link #perform(Hero)} and burst tick. */
    protected abstract void fireSingleShot(Hero hero);

    @Override
    public boolean piercesWalls() { return piercesWalls; }

    protected Location getStartLocation(Hero hero) {
        return hero.getController().getEyeLocation();
    }

    protected Vector getDirection(Location location) {
        return location.getDirection();
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
            aoe.detonate(location, trajectory, attacker, excludeVictim, damage, knockback);
        }
    }

    protected double calcDamage(Hero hero, LivingEntity victim) {
        if (dropoffDistance <= 0) return damage;
        return Math.max(minDamage, damage - (lastHitDistance / dropoffDistance));
    }

    protected double calcKnockback(Hero hero, LivingEntity victim) { return knockback; }

    protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
        double dmg = calcDamage(attacker, victim);
        double kb = calcKnockback(attacker, victim);
        LivingEntity shooter = attacker.getController().getEntity();
        switch (kbType) {
            case DIRECTION -> {
                Vector dir = currentDirection != null ? currentDirection : hitLoc.getDirection();
                Interactions.on(shooter, victim).abilityName(getName()).damage(dmg).knockback(kb).direction(dir).execute();
            }
            case HIT_LOCATION -> Interactions.on(shooter, victim).abilityName(getName()).damage(dmg).knockback(kb).fromLocation(hitLoc).execute();
            case ATTACKER -> Interactions.on(shooter, victim).abilityName(getName()).damage(dmg).knockback(kb).execute();
        }
    }

    /**
     * The sound for a direct hit. Subclasses override to SUBSTITUTE a different sound per hit
     * (e.g. Railgunner's headshot) rather than layering a second one on top.
     */
    protected SoundEffect hitSoundFor(Hero attacker, LivingEntity victim, Location hitLoc) {
        return hitSound;
    }

    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        hitSoundFor(attacker, victim, hitLoc).play(victim.getEyeLocation(), attacker.getController());
        hitVisual.play(victim.getEyeLocation());
        attacker.triggerAbilityHit(this, victim);
        applyInteraction(attacker, victim, hitLoc);
        // A direct hit is the one path that can bury the blast center: hitLoc comes from a ray-trace
        // against the victim's AABB grown by the projectile radius, so a fat shot resolves it well
        // past the victim. currentDirection is set by the subclass immediately before this call.
        detonateAoe(attacker, hitLoc, currentDirection, victim);
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim) { }

    @Override
    public void onHitWall(Hero hero, Location location) {
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
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        detonateAoe(hero, location, null);
    }
}
