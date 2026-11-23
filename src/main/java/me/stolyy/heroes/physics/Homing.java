package me.stolyy.heroes.physics;

import me.stolyy.heroes.game.GameManager;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Composition helper for entity-tracking projectiles. Encapsulates target validation,
 * retargeting, and per-tick velocity computation toward the target.
 *
 * <p>Velocity magnitude is clamped to the remaining distance to the target so a projectile
 * lands exactly on the target rather than overshooting and orbiting on the next tick. This
 * also keeps very-small-distance normalize calls benign — when {@code distance < epsilon},
 * the resulting velocity is tiny in any direction, so unstable normalization can't cause
 * the projectile to fly off-course.
 *
 * <p>The retargeter is invoked when the current target is no longer valid (despawned,
 * dead, or respawning). If it returns null, {@link #step(Location)} returns null — the
 * caller should treat that as "no target available" (typically explode/expire).
 *
 * <p>This helper does not know about projectile lifecycle, display entities, sounds, or
 * AoE. Compose it into per-instance ability state and call {@link #step(Location)} once
 * per tick to obtain the velocity to apply.
 */
public class Homing {
    private final Supplier<LivingEntity> retargeter;
    private final Predicate<LivingEntity> validity;
    private final double speed;
    private LivingEntity target;

    /**
     * @param initialTarget the initial target — may be null, in which case the retargeter
     *                      is consulted on first {@link #step(Location)} call
     * @param speed         maximum blocks per tick the projectile may travel toward the target
     * @param retargeter    invoked when the current target becomes invalid; may return null
     *                      to indicate no replacement is available
     */
    public Homing(LivingEntity initialTarget, double speed, Supplier<LivingEntity> retargeter) {
        this(initialTarget, speed, retargeter, Homing::defaultValid);
    }

    /**
     * As above, with a caller-supplied validity test. The default only checks that the entity still
     * exists and isn't respawning, which is not enough for combat targeting: a player eliminated
     * mid-flight is restored to full health and moved to the spectator team, so they stay "valid"
     * and a projectile will happily keep chasing them. Pass a game-aware predicate (e.g.
     * {@code GameManager::canTarget}) for anything that homes onto an opponent.
     *
     * <p>The predicate never receives null — {@link #step(Location)} null-checks first.
     */
    public Homing(LivingEntity initialTarget, double speed, Supplier<LivingEntity> retargeter,
                  Predicate<LivingEntity> validity) {
        this.target = initialTarget;
        this.speed = speed;
        this.retargeter = retargeter;
        this.validity = validity;
    }

    /**
     * Re-validates the target (re-targeting if needed via the supplied retargeter) and
     * returns the velocity to apply this tick toward the (possibly new) target. The
     * magnitude is {@code min(speed, distance-to-target)} so the projectile cannot
     * overshoot.
     *
     * @param current the projectile's current location
     * @return velocity vector to apply, or {@code null} if no valid target could be found
     */
    public Vector step(Location current) {
        if (!valid(target)) {
            target = retargeter.get();
            if (!valid(target)) return null;
        }

        Vector diff = target.getEyeLocation().toVector().subtract(current.toVector());
        double dist = diff.length();
        if (dist <= 0) return new Vector(0, 0, 0);

        double clampedSpeed = Math.min(speed, dist);
        return diff.multiply(clampedSpeed / dist);
    }

    /** Returns the entity currently being tracked. May be null after a retarget that found nothing. */
    public LivingEntity currentTarget() {
        return target;
    }

    private boolean valid(LivingEntity t) {
        return t != null && validity.test(t);
    }

    /** Existence-only validity: the entity is still in the world and not mid-respawn. */
    public static boolean defaultValid(LivingEntity t) {
        if (!t.isValid() || t.isDead()) return false;
        if (t instanceof Player p && GameManager.isRespawning(p)) return false;
        return true;
    }
}
