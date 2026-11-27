package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.mechanics.StatusEffects;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Combat spacing, Citizens pathing reissue guards, backpedal kiting, platform step-out, and kiting rotation focus.
 */
public class BotNavigation {
    // Vanilla MOVEMENT_SPEED 0.1 maps linearly to 4.317 blocks/s = 0.21585 blocks/tick.
    private static final double WALK_SPEED_PER_ATTR_PER_TICK = (4.317 / 20.0) / 0.1;
    private static final Vector UP = new Vector(0, 1, 0);
    private static final double DIAG = Math.sqrt(0.5);
    private static final double[][] COMPASS_DIRS = {
            {1, 0}, {DIAG, DIAG}, {0, 1}, {-DIAG, DIAG},
            {-1, 0}, {-DIAG, -DIAG}, {0, -1}, {DIAG, -DIAG}
    };

    private final Strategy strategy;
    private final MovementActions movementActions;
    private final double speedModifier;

    private LivingEntity lastNavTarget;
    private Location lastNavDestination;
    private LivingEntity navFacingFocus;
    private int lastNavIssueTick;
    private LivingEntity stepOutTarget;
    private Location stepOutTargetAnchor;
    private Location cachedStepOutPoint;

    public BotNavigation(Strategy strategy, MovementActions movementActions, double speedModifier) {
        this.strategy = strategy;
        this.movementActions = movementActions;
        this.speedModifier = speedModifier;
        this.lastNavIssueTick = -BotConfig.navReissueMinTicks();
    }

    public static boolean hasLineOfSight(BotController controller, LivingEntity target) {
        return !WallDetection.rayCast(controller.getEyeLocation(), target.getEyeLocation());
    }

    /**
     * Repositions the bot during {@code FIGHT}: chases live crystals first, then picks entity
     * chase, ring holding, or S-key backpedal kiting based on {@link Strategy#getPreferredDistance()}.
     */
    public void performFightNavigation(Hero hero, BotController controller, LivingEntity target,
                                       int tickCount, boolean seeksCrystals) {
        Location crystal = seeksCrystals ? activeCrystalLocation(controller) : null;
        if (crystal != null) {
            issueLocationNav(controller, crystal, tickCount);
            return;
        }

        double preferred = strategy.getPreferredDistance();
        double distance = controller.getLocation().distance(target.getLocation());
        if (preferred <= 0 || distance > preferred + BotConfig.ringSlack()) {
            if (!hasLineOfSight(controller, target)) {
                navigateBlockedTarget(controller, target, tickCount);
            } else {
                clearStepOut();
                navigateToEntity(controller, target, tickCount);
            }

            double horizontalDistance = Strategy.horizontalDistance(controller.getLocation(), target.getLocation());
            if (horizontalDistance > strategy.getFarDistance()) {
                controller.lookAt(target.getEyeLocation());
                movementActions.tryApproach(hero, controller, target);
            }
            return;
        }

        if (distance < preferred - BotConfig.backpedalSlack()) {
            if (!hasLineOfSight(controller, target)) {
                navigateBlockedTarget(controller, target, tickCount);
                return;
            }
            clearStepOut();

            stopAndClear(controller);
            controller.lookAt(target.getEyeLocation());

            Vector back = controller.getLocation().toVector()
                    .subtract(target.getLocation().toVector()).setY(0);
            boolean safe = back.lengthSquared() > 0.01 && !WallDetection.isAboveVoid(
                    controller.getLocation().clone().add(back.clone().normalize().multiply(2)));
            Vector current = controller.getVelocity();
            boolean slowEnough = current.clone().setY(0).lengthSquared() < BotConfig.velocityQuietSpeedSq();
            if (safe && slowEnough && !hero.status().blocks(StatusEffects.Restriction.MOVE)) {
                double effectiveSpeedAttr = hero.getAttributes().getMovementSpeed();
                Vector vel = back.normalize().multiply(effectiveSpeedAttr * WALK_SPEED_PER_ATTR_PER_TICK);
                vel.setY(current.getY());
                controller.setVelocity(vel);
            }
            return;
        }

        if (!hasLineOfSight(controller, target)) {
            navigateBlockedTarget(controller, target, tickCount);
            return;
        }
        clearStepOut();

        Location targetLoc = target.getLocation();
        Vector toBot = controller.getLocation().toVector().subtract(targetLoc.toVector()).setY(0);
        if (toBot.lengthSquared() < 0.01) toBot = new Vector(1, 0, 0);
        Location ring = targetLoc.clone().add(toBot.normalize().multiply(preferred));
        ring.setY(controller.getLocation().getY());
        if (WallDetection.isAboveVoid(ring)) {
            navigateToEntity(controller, target, tickCount);
            return;
        }
        if (ring.distanceSquared(controller.getLocation()) > BotConfig.ringHoldDistanceSq()) {
            issueLocationNav(controller, ring, tickCount);
        } else {
            if (controller.isNavigating()) controller.stopNavigation();
            lastNavDestination = null;
            controller.lookAt(target.getEyeLocation());
        }
    }

    public void navigateToEntity(BotController controller, LivingEntity entity, int tickCount) {
        if (entity == lastNavTarget && controller.isNavigating()) return;
        if (!canIssueNav(controller, tickCount)) return;
        controller.navigateToEntity(entity);
        controller.setNavigationSpeed((float) speedModifier);
        lastNavTarget = entity;
        lastNavDestination = null;
        lastNavIssueTick = tickCount;
    }

    public void issueLocationNav(BotController controller, Location dest, int tickCount) {
        if (lastNavDestination != null && controller.isNavigating()
                && lastNavDestination.distanceSquared(dest) < BotConfig.navReissueDistanceSq()) {
            return;
        }
        if (!canIssueNav(controller, tickCount)) return;
        controller.navigateTo(dest);
        controller.setNavigationSpeed((float) speedModifier);
        lastNavDestination = dest.clone();
        lastNavTarget = null;
        lastNavIssueTick = tickCount;
    }

    public void updateFacing(BotController controller, @Nullable LivingEntity focus) {
        if (focus == navFacingFocus) return;
        navFacingFocus = focus;
        controller.setNavigationLookAt(focus == null ? null : focus::getEyeLocation);
    }

    public void stopAndClear(BotController controller) {
        if (controller.isNavigating()) controller.stopNavigation();
        clearDestinationState();
    }

    public void clearDestinationState() {
        lastNavTarget = null;
        lastNavDestination = null;
        clearStepOut();
    }

    private void clearStepOut() {
        stepOutTarget = null;
        stepOutTargetAnchor = null;
        cachedStepOutPoint = null;
    }

    /**
     * When line of sight is blocked and the target sits above the bot, steps out from under the
     * intervening platform to a clear headroom point instead of camping beneath the target.
     */
    private void navigateBlockedTarget(BotController controller, LivingEntity target, int tickCount) {
        Location botLoc = controller.getLocation();
        Location targetLoc = target.getLocation();
        if (targetLoc.getY() - botLoc.getY() > BotConfig.chaseJumpHeightThreshold()) {
            Location stepOut = resolveStepOutPoint(controller, target);
            if (stepOut != null) {
                issueLocationNav(controller, stepOut, tickCount);
                return;
            }
        }
        clearStepOut();
        navigateToEntity(controller, target, tickCount);
    }

    private @Nullable Location resolveStepOutPoint(BotController controller, LivingEntity target) {
        Location targetLoc = target.getLocation();
        if (cachedStepOutPoint != null
                && stepOutTarget == target
                && stepOutTargetAnchor != null
                && stepOutTargetAnchor.getWorld().equals(targetLoc.getWorld())
                && stepOutTargetAnchor.distanceSquared(targetLoc) <= BotConfig.navReissueDistanceSq()) {
            return cachedStepOutPoint;
        }

        Location botLoc = controller.getLocation();
        LivingEntity self = controller.getEntity();
        World world = botLoc.getWorld();
        double width = self.getWidth();
        double height = self.getHeight();
        double eyeHeight = self.getEyeHeight();
        double botY = botLoc.getY();
        double targetY = targetLoc.getY();
        double minR = BotConfig.stepOutMinRadius();
        double maxR = BotConfig.stepOutMaxRadius();

        Location best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (double[] dir : COMPASS_DIRS) {
            for (double r = minR; r <= maxR + 1.0E-6; r += 1.0) {
                Location probe = new Location(world, targetLoc.getX() + dir[0] * r, botY, targetLoc.getZ() + dir[1] * r);
                if (WallDetection.isAboveVoid(probe)) continue;
                if (!WallDetection.isStandingClear(world, probe, width, height)) continue;
                double rayDist = targetY - (botY + eyeHeight);
                if (rayDist > 0) {
                    Location head = new Location(world, probe.getX(), botY + eyeHeight, probe.getZ());
                    if (world.rayTraceBlocks(head, UP, rayDist, FluidCollisionMode.NEVER, true) != null) {
                        continue;
                    }
                }
                double distSq = probe.distanceSquared(botLoc);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = probe;
                }
                break;
            }
        }

        stepOutTarget = target;
        stepOutTargetAnchor = targetLoc.clone();
        cachedStepOutPoint = best;
        return best;
    }

    public void reset() {
        clearDestinationState();
        navFacingFocus = null;
        lastNavIssueTick = -BotConfig.navReissueMinTicks();
    }

    public @Nullable Location activeCrystalLocation(BotController controller) {
        LivingEntity self = controller.getEntity();
        if (self == null) return null;
        Game game = GameManager.getEntityGame(self);
        return game == null ? null : game.activeCrystalLocation();
    }

    private boolean canIssueNav(BotController controller, int tickCount) {
        return controller.isOnGround()
                && tickCount - lastNavIssueTick >= BotConfig.navReissueMinTicks()
                && controller.getVelocity().lengthSquared() < BotConfig.velocityQuietSpeedSq();
    }
}
