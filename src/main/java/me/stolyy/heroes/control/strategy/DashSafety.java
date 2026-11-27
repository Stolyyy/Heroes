package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.kit.shoop.ShoopRTL;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Landing-safety veto and ride-cancel telemetry for movement abilities that carry the caster
 * across space ({@link Dash} and {@link ShoopRTL}).
 */
public final class DashSafety {
    private static final double RTL_STEP = 1.0;
    private static final double RTL_VOID_LOOKAHEAD_MULT = 1.5;

    private DashSafety() {
    }

    /**
     * @param direction the direction the ability will launch along — the caster's facing at
     *                  fire time for a live check, or the intended aim direction for a
     *                  look-before-you-leap check made just before {@code lookAt} is issued.
     */
    public static boolean isLandingSafe(Hero hero, BotController controller, Ability ability, Vector direction) {
        if (direction.lengthSquared() < 0.01) return true;

        Game game = GameManager.getEntityGame(controller.getEntity());
        if (ability instanceof Dash dash) {
            double projectedDistance = dash.getSpeed() * dash.getDurationTicks();
            Location landing = controller.getLocation().clone()
                    .add(direction.clone().normalize().multiply(projectedDistance));
            if (!inBounds(game, landing)) return false;
            if (!WallDetection.isAboveVoid(landing)) return true;
            return hero.getMobility().getRemainingJumps() >= 1;
        }

        if (ability instanceof ShoopRTL rtl) {
            return isRideLaunchSafe(hero, controller, rtl, game, direction);
        }

        return true;
    }

    private static boolean isRideLaunchSafe(Hero hero, BotController controller, ShoopRTL rtl,
                                            Game game, Vector direction) {
        Vector dir = direction.clone().normalize();
        Location start = controller.getEyeLocation();
        double maxDist = clampToWall(start, dir, rtl.projectedDistance(hero));
        double commitDist = Math.min(maxDist, rtl.minCancelDistance());

        for (double d = RTL_STEP; d < commitDist; d += RTL_STEP) {
            Location probe = start.clone().add(dir.clone().multiply(d));
            if (!inBounds(game, probe)) return false;
        }
        Location commitEnd = start.clone().add(dir.clone().multiply(commitDist));
        return inBounds(game, commitEnd) && !WallDetection.isAboveVoid(commitEnd);
    }

    /** True when an active Shoop ride is about to cross an edge, leave arena bounds, or stay over void. */
    public static boolean shouldDismountRide(Hero hero, BotController controller) {
        ShoopRTL activeRide = findActiveRide(hero);
        if (activeRide == null) return false;

        Location loc = activeRide.rideLocation();
        Vector vel = activeRide.rideVelocity();
        if (loc == null || vel == null || vel.lengthSquared() < 1.0E-6) return false;

        Game game = GameManager.getEntityGame(controller.getEntity());
        Location next = loc.clone().add(vel);
        Location lookahead = loc.clone().add(vel.clone().multiply(RTL_VOID_LOOKAHEAD_MULT));
        if (!inBounds(game, loc) || !inBounds(game, next) || !inBounds(game, lookahead)) {
            return true;
        }

        if (!WallDetection.isAboveVoid(loc)) {
            return WallDetection.isAboveVoid(next) || WallDetection.isAboveVoid(lookahead);
        }

        // Already over the void: keep riding only if solid stage lies ahead along the remaining path.
        Vector dir = vel.clone().normalize();
        double remaining = clampToWall(loc, dir, activeRide.remainingProjectedDistance(hero));
        for (double d = RTL_VOID_LOOKAHEAD_MULT; d < remaining; d += RTL_VOID_LOOKAHEAD_MULT) {
            Location probe = loc.clone().add(dir.clone().multiply(d));
            if (!inBounds(game, probe)) return true;
            if (!WallDetection.isAboveVoid(probe)) return false;
        }
        if (remaining > 0) {
            Location end = loc.clone().add(dir.clone().multiply(remaining));
            return !inBounds(game, end) || WallDetection.isAboveVoid(end);
        }
        return true;
    }

    private static ShoopRTL findActiveRide(Hero hero) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive instanceof ShoopRTL rtl && rtl.isRiding()) {
                return rtl;
            }
        }
        return null;
    }

    private static double clampToWall(Location start, Vector dir, double maxDistance) {
        if (maxDistance <= 0) return 0;
        RayTraceResult wall = WallDetection.rayTraceBlock(start, dir, maxDistance);
        if (wall == null) return maxDistance;
        return Math.min(maxDistance, wall.getHitPosition().distance(start.toVector()));
    }

    private static boolean inBounds(Game game, Location loc) {
        if (game == null) return true;
        return loc.getWorld() != null
                && loc.getWorld().equals(game.settings().map().world())
                && game.settings().boundaries().contains(loc.toVector());
    }
}
