package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.strategy.DashSafety;
import me.stolyy.heroes.control.strategy.JumpSafety;
import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Priority-ordered movement-tool loadout ({@code jump | primary | secondary | smash | sneak |
 * drop | swap}) shared by {@link BotRecovery}, {@link BotEvade}, and far-chase approaches.
 */
public class MovementActions {
    private static final Set<String> VALID_SLOTS = Set.of(
            "jump", "primary", "secondary", "smash", "sneak", "drop", "swap"
    );

    private record MovementAction(String action, int priority, int delayAfterTicks, boolean evadeOnly) {}

    private final List<MovementAction> actions = new ArrayList<>();
    private final Set<String> slots = new HashSet<>();
    private int delayLeft;

    public MovementActions(ConfigurationSection movementSection) {
        if (movementSection != null) {
            for (var raw : movementSection.getMapList("actions")) {
                Object action = raw.get("action");
                Object priority = raw.get("priority");
                Object delay = raw.get("delay_after_ticks");
                if (action == null) {
                    throw new IllegalArgumentException("movement.actions entry is missing 'action' key");
                }
                String slot = action.toString().toLowerCase();
                if (!VALID_SLOTS.contains(slot)) {
                    throw new IllegalArgumentException("Unknown movement.actions slot: '" + slot + "'");
                }
                if (!(priority instanceof Number priorityNum)) {
                    throw new IllegalArgumentException("movement.actions entry '" + slot + "' is missing numeric 'priority'");
                }
                if (!(delay instanceof Number delayNum)) {
                    throw new IllegalArgumentException("movement.actions entry '" + slot + "' is missing numeric 'delay_after_ticks'");
                }
                boolean evadeOnly = Boolean.TRUE.equals(raw.get("evade_only"));
                slots.add(slot);
                actions.add(new MovementAction(slot, priorityNum.intValue(), delayNum.intValue(), evadeOnly));
            }
        }
        if (actions.isEmpty()) {
            actions.add(new MovementAction("jump", 1, 8, false));
        }
        actions.sort(Comparator.comparingInt(MovementAction::priority));
    }

    /** True when this slot is reserved for movement tools during RECOVER or EVADE. */
    public boolean reserves(String slot) {
        return slots.contains(slot);
    }

    public void reset() {
        delayLeft = 0;
    }

    /** Fires the first usable movement tool once any post-fire commit delay has elapsed. */
    public void tick(Hero hero, BotController controller, boolean allowAirborneJumps) {
        runActions(action -> !(allowAirborneJumps && action.evadeOnly())
                && fire(hero, controller, action.action(), allowAirborneJumps));
    }

    /** Gap-closer toward {@code approachTarget} during far FIGHT chases, vetting landing safety. */
    public boolean tryApproach(Hero hero, BotController controller, LivingEntity approachTarget) {
        return runActions(action -> fireApproach(hero, controller, action.action(), approachTarget));
    }

    private boolean runActions(Predicate<MovementAction> dispatcher) {
        if (delayLeft > 0) {
            delayLeft--;
            return false;
        }
        for (MovementAction action : actions) {
            if (dispatcher.test(action)) {
                delayLeft = action.delayAfterTicks();
                return true;
            }
        }
        return false;
    }

    private boolean fire(Hero hero, BotController controller, String action, boolean allowAirborneJumps) {
        Vector safetyDir = allowAirborneJumps ? null : controller.getLocation().getDirection();
        return switch (action) {
            case "jump" -> (allowAirborneJumps || controller.isOnGround()) && hero.getMobility().performJump(hero);
            case "primary" -> fireAbility(hero, controller, hero.getPrimaryAbility(), hero::usePrimary, safetyDir);
            case "secondary" -> fireAbility(hero, controller, hero.getSecondaryAbility(), hero::useSecondary, safetyDir);
            case "smash" -> fireAbility(hero, controller, hero.getSmashAbility(), hero::useSmash, safetyDir);
            case "sneak" -> firePassive(hero, controller, AbilityType.PASSIVE_SNEAK, hero::onSneak, safetyDir);
            case "drop" -> firePassive(hero, controller, AbilityType.PASSIVE_DROP, hero::onDrop, safetyDir);
            case "swap" -> firePassive(hero, controller, AbilityType.PASSIVE_SWAP, hero::onSwap, safetyDir);
            default -> throw new IllegalStateException("Unknown movement action: " + action);
        };
    }

    private boolean fireApproach(Hero hero, BotController controller, String action, LivingEntity approachTarget) {
        Vector towardTarget = approachTarget.getLocation().toVector().subtract(controller.getLocation().toVector());
        return switch (action) {
            case "jump" -> controller.isOnGround()
                    && JumpSafety.isChaseJumpSafe(hero, controller, approachTarget)
                    && hero.getMobility().performJump(hero);
            case "primary" -> fireAbility(hero, controller, hero.getPrimaryAbility(), hero::usePrimary, towardTarget);
            case "secondary" -> fireAbility(hero, controller, hero.getSecondaryAbility(), hero::useSecondary, towardTarget);
            case "smash" -> fireAbility(hero, controller, hero.getSmashAbility(), hero::useSmash, towardTarget);
            default -> false;
        };
    }

    private boolean fireAbility(Hero hero, BotController controller, Ability ability,
                                Runnable input, @Nullable Vector safetyDirection) {
        if (ability == null || !ability.canExecute(hero)) return false;
        if (safetyDirection != null && !DashSafety.isLandingSafe(hero, controller, ability, safetyDirection)) {
            return false;
        }
        input.run();
        return true;
    }

    private boolean firePassive(Hero hero, BotController controller, AbilityType type,
                                Runnable input, @Nullable Vector safetyDirection) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive.getType() == type && passive.canExecute(hero)) {
                if (safetyDirection != null && !DashSafety.isLandingSafe(hero, controller, passive, safetyDirection)) {
                    continue;
                }
                input.run();
                return true;
            }
        }
        return false;
    }
}
