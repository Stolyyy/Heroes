package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The EVADE task's brain: a MELEE/HYBRID threat is inside panic_distance, so open the gap.
 * Flee-pathing is vanilla-AvoidEntityGoal-style (the ocelot pattern): sample random candidate
 * points in the hemisphere AWAY from the threat, keep the ones with stage under them, path to
 * the best — the PATHFINDER routes around walls, so corners resolve without explicit cornered
 * detection. Meanwhile the hero's movement tools fire aimed along the escape.
 *
 * <p>No attacking while evading — that is the ladder's contract. {@link #tick} returns false
 * when there is NO safe flee direction (backed against the void): the caller falls through to
 * FIGHT, because a cornered bot stands and fights rather than pacing at the ledge.
 */
public class BotEvade {
    private static final double FLEE_MIN_BLOCKS = 8.0;
    private static final double FLEE_MAX_BLOCKS = 14.0;
    private static final int FLEE_CANDIDATES = 6;
    private static final double FLEE_SPREAD_DEGREES = 60.0;
    private static final double VELOCITY_QUIET_SQ = 0.09; // same gate as BaseBotAI.canIssueNav

    private final MovementActions movementActions;
    private final double speedModifier;
    private Location fleeTarget;

    public BotEvade(MovementActions movementActions, double speedModifier) {
        this.movementActions = movementActions;
        this.speedModifier = speedModifier;
    }

    public void reset() {
        fleeTarget = null;
    }

    /** One evade tick. False = cornered (no safe flee point) — caller should FIGHT instead. */
    public boolean tick(Hero hero, BotController controller, LivingEntity threat) {
        // Riding a movement tool (shoop's RTL rocket): the ride IS the escape — no navigation,
        // no extra actions. Cancel it the moment it crosses off the stage (a second sneak past
        // the reactivation window), or the full ride carries the bot clean off the map.
        LivingEntity self = controller.getEntity();
        if (self != null && self.isInsideVehicle()) {
            if (WallDetection.isAboveVoid(controller.getLocation())) {
                hero.onSneak();
            }
            return true;
        }

        // Re-pick the flee point when there is none or the previous hop finished — but only
        // while GROUNDED and at walking pace. Mid-hop the navigator is left alone (re-issuing
        // restarts pathfinding and freezes the NPC), Citizens can't path from the air at all
        // (an airborne re-issue loop suspends gravity and hangs the bot in place), and a nav
        // issue in the tick after taking knockback would ZERO the launch — the fleeing bot
        // must ride the hit out before pathing resumes.
        if ((fleeTarget == null || !controller.isNavigating()) && controller.isOnGround()
                && controller.getVelocity().lengthSquared() < VELOCITY_QUIET_SQ) {
            fleeTarget = pickFleePoint(controller, threat);
            if (fleeTarget != null) {
                controller.navigateTo(fleeTarget);
                controller.setNavigationSpeed((float) speedModifier);
            }
        }
        if (fleeTarget == null) return false;

        // Physical aim away: movement tools launch along the SERVER-side facing (the packet-
        // level kiting override only changes what viewers see). Aimed slightly up so escape
        // jumps and pounces get their arc.
        Vector away = awayDirection(controller, threat);
        Location aim = controller.getLocation().clone().add(away.multiply(8));
        aim.setY(controller.getLocation().getY() + 1.5);
        controller.lookAt(aim);
        movementActions.tick(hero, controller, false); // grounded escape hops only — see MovementActions
        return true;
    }

    private Vector awayDirection(BotController controller, LivingEntity threat) {
        Vector away = controller.getLocation().toVector()
                .subtract(threat.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01) {
            // Overlapped: any direction opens the gap — pick a random one.
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            return new Vector(Math.cos(angle), 0, Math.sin(angle));
        }
        return away.normalize();
    }

    private Location pickFleePoint(BotController controller, LivingEntity threat) {
        Location botLoc = controller.getLocation();
        Vector away = awayDirection(controller, threat);
        Location best = null;
        double bestScore = -1;
        for (int i = 0; i < FLEE_CANDIDATES; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(-FLEE_SPREAD_DEGREES, FLEE_SPREAD_DEGREES);
            double dist = ThreadLocalRandom.current().nextDouble(FLEE_MIN_BLOCKS, FLEE_MAX_BLOCKS);
            Vector dir = away.clone().rotateAroundY(Math.toRadians(angle));
            Location candidate = botLoc.clone().add(dir.multiply(dist));
            if (WallDetection.isAboveVoid(candidate)) continue;
            double score = candidate.distanceSquared(threat.getLocation());
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }
}
