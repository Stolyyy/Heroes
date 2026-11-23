package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Standalone AoE detonation utility that any ability can use.
 * Not an abstract template — a concrete helper class that reads its parameters
 * from a ConfigurationSection and provides a detonate() method.
 */
public class Aoe {

    // Granularity of the walk back out of geometry. Fine enough that the recovered center hugs the
    // block face it came from, coarse enough that a full-radius walk is a handful of block lookups.
    private static final double ORIGIN_STEP = 0.25;

    private final Ability sourceAbility;
    private final double radius;
    private final double damage;         // 0 means "use fallback from caller"
    private final double knockback;      // 0 means "use fallback from caller"
    private final boolean piercesWalls;
    // aoe.kb_type: HIT_LOCATION radiates knockback out from the blast center; anything else
    // (the default) pushes attacker→victim by player positions. Center-radial is deliberately
    // opt-in for the handful of big blasts (bulk car, kame-do, clusterbuck, rocket punch).
    private final boolean kbFromCenter;
    private final VisualEffect hitVisual;
    private final SoundEffect hitSound;
    private final VisualEffect centerVisual;
    private final SoundEffect centerSound;

    public Aoe(ConfigurationSection section, Ability sourceAbility) {
        this.sourceAbility = sourceAbility;

        this.radius = Configs.requireDouble(section, "radius");
        this.damage = Configs.requireDouble(section, "damage");
        this.knockback = Configs.requireDouble(section, "knockback");

        // Independent of the sibling abilities.<slot>.pierces_walls, which decides whether the
        // projectile survives contact with a wall. A rocket can stop dead on a wall and still have
        // its blast wrap the corner, or punch through and detonate with a wall-shadowed blast.
        this.piercesWalls = section.getBoolean("pierces_walls", false);
        this.kbFromCenter = "HIT_LOCATION".equalsIgnoreCase(section.getString("kb_type", ""));

        this.hitVisual = new VisualEffect(section.getConfigurationSection("hit_visual"));
        this.hitSound = new SoundEffect(section.getConfigurationSection("hit_sound"));
        this.centerVisual = new VisualEffect(section.getConfigurationSection("center_visual"));
        this.centerSound = new SoundEffect(section.getConfigurationSection("center_sound"));
    }

    /**
     * Detonates an AoE explosion at the given center location.
     *
     * @param rawCenter        Where the caller believes the blast happened, before origin resolution.
     * @param trajectory       Travel direction of the shot that produced this blast, used to back the
     *                         center out of geometry. Null falls back to the attacker's eye.
     * @param attacker         The hero causing the explosion.
     * @param excludeVictim    An optional LivingEntity to exclude from hits (e.g. the projectile's original target).
     * @param fallbackDamage   Damage to use if this Aoe's damage is 0.
     * @param fallbackKnockback Knockback to use if this Aoe's knockback is 0.
     * @param onHitCallback    Optional callback invoked for each victim hit, with the victim and hit location.
     */
    public void detonate(Location rawCenter, @Nullable Vector trajectory, Hero attacker,
                         @Nullable LivingEntity excludeVictim,
                         double fallbackDamage, double fallbackKnockback,
                         @Nullable BiConsumer<LivingEntity, Location> onHitCallback) {

        Location center = resolveCenter(rawCenter, trajectory, attacker);

        centerVisual.play(center, attacker.getSkin().getId());
        centerSound.play(center, attacker.getController());

        final double effectiveDmg = damage > 0 ? damage : fallbackDamage;
        final double effectiveKb = knockback > 0 ? knockback : fallbackKnockback;

        Hitbox hitbox = Hitbox.sphere(center, radius)
                .exclude(attacker.getController().getEntity())
                .excludeTeammates(attacker.getController().getEntity())
                .checkWalls(!piercesWalls)
                .pierce(true);

        if (excludeVictim != null) {
            hitbox = hitbox.exclude(excludeVictim);
        }

        hitbox.onHit((LivingEntity target, Location hitLoc) -> {
            hitSound.play(target.getEyeLocation(), attacker.getController());
            hitVisual.play(target.getEyeLocation());

            Interactions.InteractionBuilder interaction =
                    Interactions.on(attacker.getController().getEntity(), target)
                            .abilityName(sourceAbility != null ? sourceAbility.getName() : null)
                            .damage(effectiveDmg)
                            .knockback(effectiveKb);
            // Default is POSITIONAL (attacker→victim by player positions); HIT_LOCATION opts a
            // blast into radiating from its center instead.
            if (kbFromCenter) interaction.fromLocation(center);
            interaction.execute();

            attacker.triggerAbilityHit(sourceAbility, target);

            if (onHitCallback != null) {
                onHitCallback.accept(target, hitLoc);
            }
        }).check();
    }

    /**
     * Convenience overload without a per-hit callback.
     */
    public void detonate(Location center, @Nullable Vector trajectory, Hero attacker,
                         @Nullable LivingEntity excludeVictim,
                         double fallbackDamage, double fallbackKnockback) {
        detonate(center, trajectory, attacker, excludeVictim, fallbackDamage, fallbackKnockback, null);
    }

    /**
     * Convenience overload for callers with no trajectory to offer (planted charges, zone pulses).
     */
    public void detonate(Location center, Hero attacker, @Nullable LivingEntity excludeVictim,
                         double fallbackDamage, double fallbackKnockback) {
        detonate(center, null, attacker, excludeVictim, fallbackDamage, fallbackKnockback, null);
    }

    /**
     * A direct hit resolves its center by ray-tracing the victim's AABB expanded by the PROJECTILE
     * radius, so a fat projectile can plant it a full radius past the victim — for bulk (1.5) that is
     * a block and a half under their feet, buried in the floor. A center sunk r/2 deep leaves only
     * ~16% of the blast volume above ground and turns the radial knockback vector near-vertical,
     * which reads as "the rocket did nothing". Retreat along the path the shot arrived on until the
     * center is clear of geometry, capped at the blast radius so a detonation can never relocate
     * outside its own sphere. Runs before any visual so particle, damage sphere and knockback origin
     * all agree on where the explosion was.
     */
    private Location resolveCenter(Location center, @Nullable Vector trajectory, Hero attacker) {
        if (!WallDetection.isInsideSolid(center)) return center;

        Vector back;
        if (trajectory != null && trajectory.lengthSquared() > 1.0e-6) {
            back = trajectory.clone().normalize().multiply(-1);
        } else {
            back = attacker.getController().getEyeLocation().toVector().subtract(center.toVector());
            // Detonating exactly on the attacker's eye leaves no direction to retreat along.
            if (back.lengthSquared() < 1.0e-6) return center;
        }

        return WallDetection.retreatFromSolid(center, back, radius, ORIGIN_STEP);
    }
}
