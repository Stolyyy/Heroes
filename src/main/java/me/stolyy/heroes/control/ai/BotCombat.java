package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.strategy.DashSafety;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.hero.Hero;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Input-delay queue, ability priority evaluation, ult HP gating, and jab execution for bots.
 */
public class BotCombat {
    private record PendingAction(long executeAt, Location aimLocation, Runnable action) {}

    private final Strategy strategy;
    private final MovementActions movementActions;
    private long inputDelayMs;
    private PendingAction pendingAction;

    public BotCombat(Strategy strategy, MovementActions movementActions, long inputDelayMs) {
        this.strategy = strategy;
        this.movementActions = movementActions;
        this.inputDelayMs = inputDelayMs;
    }

    public void setInputDelayTicks(int ticks) {
        this.inputDelayMs = ticks * Heroes.MS_PER_TICK;
    }

    public void clearPending() {
        pendingAction = null;
    }

    public void reset() {
        pendingAction = null;
    }

    public void tick(Hero hero, BotController controller, @Nullable LivingEntity target,
                     Supplier<List<LivingEntity>> nearbyEnemies, boolean reserveMovementTools) {
        boolean acted = processPendingAction(controller);
        if (!acted && pendingAction == null && target != null) {
            performCombat(hero, controller, target, nearbyEnemies, reserveMovementTools);
        }
    }

    private boolean processPendingAction(BotController controller) {
        if (pendingAction == null) return false;
        if (System.currentTimeMillis() < pendingAction.executeAt()) return false;

        if (pendingAction.aimLocation() != null) {
            controller.lookAt(pendingAction.aimLocation());
        }
        pendingAction.action().run();
        pendingAction = null;
        return true;
    }

    private void queueAction(BotController controller, Location aimLocation, Runnable action) {
        if (pendingAction != null) return;
        if (inputDelayMs <= 0) {
            if (aimLocation != null) controller.lookAt(aimLocation);
            action.run();
            return;
        }
        pendingAction = new PendingAction(System.currentTimeMillis() + inputDelayMs, aimLocation, action);
    }

    private void performCombat(Hero hero, BotController controller, LivingEntity target,
                               Supplier<List<LivingEntity>> nearbyEnemies, boolean reserveMovementTools) {
        double distance = controller.getLocation().distance(target.getLocation());
        // Aim at feet because Citizens faceLocation rotates from entity feet while abilities fire from eyes.
        Location targetLoc = target.getLocation().clone();

        if (!(reserveMovementTools && movementActions.reserves("smash"))
                && shouldAttemptUlt(controller, target)
                && tryUseAbility(hero, controller, hero.getSmashAbility(), AbilityType.SMASH, target, distance, targetLoc)) {
            return;
        }
        if (!(reserveMovementTools && movementActions.reserves("primary"))
                && tryUseAbility(hero, controller, hero.getPrimaryAbility(), AbilityType.PRIMARY, target, distance, targetLoc)) {
            return;
        }
        if (!(reserveMovementTools && movementActions.reserves("secondary"))
                && tryUseAbility(hero, controller, hero.getSecondaryAbility(), AbilityType.SECONDARY, target, distance, targetLoc)) {
            return;
        }

        double jabRange = hero.getCombat().getRange();
        if (jabRange <= 0) return;
        LivingEntity jabTarget = findClosestEnemyInRange(controller, nearbyEnemies.get(), jabRange);
        if (jabTarget == null) return;

        Location jabAim = jabTarget.getLocation().clone();
        queueAction(controller, jabAim, () -> {
            if (controller.getEntity() == null) return;
            if (controller.getLocation().distanceSquared(jabTarget.getLocation()) > jabRange * jabRange) return;
            if (!BotNavigation.hasLineOfSight(controller, jabTarget)) return;
            controller.swingMainHand();
            hero.getCombat().performJab(controller, jabTarget);
        });
    }

    private boolean shouldAttemptUlt(BotController controller, LivingEntity target) {
        double minTarget = strategy.getUltMinTargetHpPercent();
        double minSelf = strategy.getUltMinSelfHpPercent();
        if (minTarget <= 0 && minSelf <= 0) return true;

        if (minTarget > 0 && hpFraction(target) < minTarget) return false;
        if (minSelf > 0) {
            LivingEntity self = controller.getEntity();
            if (self == null) return false;
            if (hpFraction(self) < minSelf && !onLastLife(self)) return false;
        }
        return true;
    }

    private boolean onLastLife(LivingEntity self) {
        Game game = GameManager.getEntityGame(self);
        if (game == null) return false;
        GamePlayer gamePlayer = game.getGamePlayer(self.getUniqueId());
        return gamePlayer != null && gamePlayer.currentLives() <= 1;
    }

    private double hpFraction(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        AttributeInstance maxAttr = Objects.requireNonNull(
                entity.getAttribute(Attribute.MAX_HEALTH),
                "Entity missing MAX_HEALTH attribute: " + entity.getName());
        double max = maxAttr.getValue();
        return max <= 0 ? 0 : entity.getHealth() / max;
    }

    private boolean tryUseAbility(Hero hero, BotController controller, Ability ability, AbilityType type,
                                  LivingEntity target, double distance, Location targetLoc) {
        if (ability == null || !ability.canExecute(hero)) return false;
        if (!strategy.shouldUseAbility(hero, type, target, distance)) return false;

        Vector towardTarget = targetLoc.toVector().subtract(controller.getLocation().toVector());
        if (!DashSafety.isLandingSafe(hero, controller, ability, towardTarget)) return false;

        if (!ability.piercesWalls() && !BotNavigation.hasLineOfSight(controller, target)) {
            return false;
        }

        Runnable action = switch (type) {
            case PRIMARY -> hero::usePrimary;
            case SECONDARY -> hero::useSecondary;
            case SMASH -> hero::useSmash;
            default -> null;
        };
        if (action == null) return false;

        queueAction(controller, targetLoc, action);
        return true;
    }

    private LivingEntity findClosestEnemyInRange(BotController controller, List<LivingEntity> nearbyEnemies,
                                                 double range) {
        Location botLoc = controller.getLocation();
        double rangeSq = range * range;
        LivingEntity closest = null;
        double closestDistSq = rangeSq + 1;

        for (LivingEntity entity : nearbyEnemies) {
            double distSq = botLoc.distanceSquared(entity.getLocation());
            if (distSq > rangeSq || distSq >= closestDistSq) continue;
            if (!BotNavigation.hasLineOfSight(controller, entity)) continue;
            closest = entity;
            closestDistSq = distSq;
        }
        return closest;
    }
}
