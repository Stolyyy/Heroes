package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Combat spacing, Citizens pathing reissue guards, backpedal kiting, and kiting rotation focus.
 */
public class BotNavigation {
    // Vanilla MOVEMENT_SPEED 0.1 maps linearly to 4.317 blocks/s = 0.21585 blocks/tick.
    private static final double WALK_SPEED_PER_ATTR_PER_TICK = (4.317 / 20.0) / 0.1;

    private final Strategy strategy;
    private final MovementActions movementActions;
    private final double speedModifier;

    private LivingEntity lastNavTarget;
    private Location lastNavDestination;
    private LivingEntity navFacingFocus;
    private int lastNavIssueTick;

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
            navigateToEntity(controller, target, tickCount);

            double horizontalDistance = Strategy.horizontalDistance(controller.getLocation(), target.getLocation());
            if (horizontalDistance > strategy.getFarDistance()) {
                controller.lookAt(target.getEyeLocation());
                movementActions.tryApproach(hero, controller, target);
            }
            return;
        }

        if (distance < preferred - BotConfig.backpedalSlack()) {
            if (!hasLineOfSight(controller, target)) {
                navigateToEntity(controller, target, tickCount);
                return;
            }

            stopAndClear(controller);
            controller.lookAt(target.getEyeLocation());

            Vector back = controller.getLocation().toVector()
                    .subtract(target.getLocation().toVector()).setY(0);
            boolean safe = back.lengthSquared() > 0.01 && !WallDetection.isAboveVoid(
                    controller.getLocation().clone().add(back.clone().normalize().multiply(2)));
            Vector current = controller.getVelocity();
            boolean slowEnough = current.clone().setY(0).lengthSquared() < BotConfig.velocityQuietSpeedSq();
            if (safe && slowEnough) {
                double effectiveSpeedAttr = hero.getAttributes().getMovementSpeed();
                Vector vel = back.normalize().multiply(effectiveSpeedAttr * WALK_SPEED_PER_ATTR_PER_TICK);
                vel.setY(current.getY());
                controller.setVelocity(vel);
            }
            return;
        }

        if (!hasLineOfSight(controller, target)) {
            navigateToEntity(controller, target, tickCount);
            return;
        }

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
