package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * EVADE task handler: opens distance when a close-range threat enters {@code panic_distance},
 * sampling candidate points away from the threat and spending grounded escape movement tools.
 */
public class BotEvade {
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

    /** Returns false when no safe flee point exists (cornered at void) so the caller fights instead. */
    public boolean tick(Hero hero, BotController controller, LivingEntity threat) {
        LivingEntity self = controller.getEntity();
        if (self != null && self.isInsideVehicle()) {
            return true;
        }

        if ((fleeTarget == null || !controller.isNavigating()) && controller.isOnGround()
                && controller.getVelocity().lengthSquared() < BotConfig.velocityQuietSpeedSq()) {
            fleeTarget = pickFleePoint(controller, threat);
            if (fleeTarget != null) {
                controller.navigateTo(fleeTarget);
                controller.setNavigationSpeed((float) speedModifier);
            }
        }
        if (fleeTarget == null) return false;

        Vector away = awayDirection(controller, threat);
        Location aim = controller.getLocation().clone().add(away.multiply(BotConfig.evadeAimDistance()));
        aim.setY(controller.getLocation().getY() + BotConfig.evadeAimHeightOffset());
        controller.lookAt(aim);
        movementActions.tick(hero, controller, false);
        return true;
    }

    private Vector awayDirection(BotController controller, LivingEntity threat) {
        Vector away = controller.getLocation().toVector()
                .subtract(threat.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01) {
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
        int candidates = BotConfig.evadeFleeCandidates();
        double spread = BotConfig.evadeFleeSpreadDegrees();
        double minBlocks = BotConfig.evadeFleeMinBlocks();
        double maxBlocks = BotConfig.evadeFleeMaxBlocks();
        for (int i = 0; i < candidates; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(-spread, spread);
            double dist = ThreadLocalRandom.current().nextDouble(minBlocks, maxBlocks);
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
