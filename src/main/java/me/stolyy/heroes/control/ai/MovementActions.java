package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.strategy.DashSafety;
import me.stolyy.heroes.control.strategy.JumpSafety;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A hero's configured movement-tool loadout (yml {@code movement.actions}): input slots
 * ({@code jump | primary | secondary | smash | sneak | drop | swap}) tried
 * lowest-priority-number first, each with a post-fire commit window ({@code
 * delay_after_ticks}) so a dash can't cancel the double jump it just spent. ONE list per
 * hero, shared by RECOVER (aimed at the stage anchor), EVADE (aimed away from the threat),
 * and FIGHT's far-chase gap-closer ({@link #tryApproach}, aimed at the enemy) — the same
 * mobility tools serve all three jobs, context supplies the aim, and the commit window
 * deliberately spans task switches.
 *
 * <p>The loop is GREEDY WITH REASSESSMENT, not predictive: each call fires the first
 * currently-usable tool ({@code canExecute} answers cooldown/energy/ammo), and the caller
 * simply asks again after the delay. Variable-distance tools (shoop's energy flight) just
 * mean another iteration; repeatable tools (pug's pounce) re-fire while resources last.
 * Heroes with no movement section get a jump-only default.
 */
public class MovementActions {
    private record MovementAction(String action, int priority, int delayAfterTicks) {}

    private final List<MovementAction> actions = new ArrayList<>();
    private final java.util.Set<String> slots = new java.util.HashSet<>();
    private int delayLeft;

    /** True when this input slot doubles as a movement tool — combat must not burn it while
     *  RECOVER/EVADE own it (it's the escape/recovery fuel, not damage). */
    public boolean reserves(String slot) {
        return slots.contains(slot);
    }

    public MovementActions(ConfigurationSection movementSection) {
        if (movementSection != null) {
            for (var raw : movementSection.getMapList("actions")) {
                Object action = raw.get("action");
                Object priority = raw.get("priority");
                Object delay = raw.get("delay_after_ticks");
                if (action == null) continue;
                String slot = action.toString().toLowerCase();
                slots.add(slot);
                actions.add(new MovementAction(slot,
                        priority instanceof Number n ? n.intValue() : Integer.MAX_VALUE,
                        delay instanceof Number n ? n.intValue() : 8));
            }
        }
        if (actions.isEmpty()) {
            // No movement section: double jumps are every hero's floor.
            actions.add(new MovementAction("jump", 1, 8));
        }
        actions.sort(Comparator.comparingInt(MovementAction::priority));
    }

    public void reset() {
        delayLeft = 0;
    }

    /**
     * Counts down the commit window, then fires the first usable tool. AIM FIRST — every
     * movement tool launches along the caster's server-side facing, so the caller must
     * {@code lookAt} the intended direction immediately before calling.
     *
     * @param allowAirborneJumps RECOVER passes true (pumping every jump mid-air is the whole
     *        point); EVADE passes false — grounded escape hops only, because chaining air
     *        jumps every delay window (worst with energy-fueled jump overrides like
     *        cakemonster's Defecake, which costs no jump charge) reads as a bot flying
     *        across the arena.
     */
    public void tick(Hero hero, me.stolyy.heroes.control.BotController controller,
                     boolean allowAirborneJumps) {
        if (delayLeft > 0) {
            delayLeft--;
            return;
        }
        for (MovementAction action : actions) {
            if (fire(hero, controller, action.action(), allowAirborneJumps)) {
                delayLeft = action.delayAfterTicks();
                return;
            }
        }
        // Nothing usable this tick (cooldowns / energy / jumps spent): the caller keeps
        // aiming and keeps asking.
    }

    /** Fires one input slot if it is currently usable; true = it fired (spend the delay). */
    private boolean fire(Hero hero, me.stolyy.heroes.control.BotController controller,
                         String action, boolean allowAirborneJumps) {
        switch (action) {
            case "jump" -> {
                if (!allowAirborneJumps && !controller.isOnGround()) return false;
                return hero.getMobility().performJump(hero); // internally gated on canJump
            }
            case "primary" -> {
                return fireAbility(hero, controller, hero.getPrimaryAbility(), hero::usePrimary, allowAirborneJumps);
            }
            case "secondary" -> {
                return fireAbility(hero, controller, hero.getSecondaryAbility(), hero::useSecondary, allowAirborneJumps);
            }
            case "smash" -> {
                return fireAbility(hero, controller, hero.getSmashAbility(), hero::useSmash, allowAirborneJumps);
            }
            case "sneak" -> {
                return firePassive(hero, AbilityType.PASSIVE_SNEAK, hero::onSneak);
            }
            case "drop" -> {
                return firePassive(hero, AbilityType.PASSIVE_DROP, hero::onDrop);
            }
            case "swap" -> {
                return firePassive(hero, AbilityType.PASSIVE_SWAP, hero::onSwap);
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * {@code allowAirborneJumps} doubles as the RECOVER signal here: {@link BotRecovery} is
     * the only caller that ever passes true (pumping every jump mid-air IS recovery), while
     * {@link BotEvade} always passes false. RECOVER is "no give-up" by design (recover-until-
     * dead) — a dash landing over the void is exactly what getting back onto the stage looks
     * like from here, so the {@link DashSafety} veto is skipped for it. EVADE gets the veto,
     * same as the FIGHT-only approach path below ({@link #tryApproach}).
     */
    private boolean fireAbility(Hero hero, me.stolyy.heroes.control.BotController controller,
                                Ability ability, Runnable input, boolean allowAirborneJumps) {
        if (ability == null || !ability.canExecute(hero)) return false;
        if (!allowAirborneJumps && !DashSafety.isLandingSafe(
                hero, controller, ability, controller.getLocation().getDirection())) {
            return false;
        }
        input.run(); // through the same input surface players use — overrides/locks apply
        return true;
    }

    /**
     * FIGHT-only gap-closer (BaseBotAI.performNavigation's far-chase branch): fires the first
     * currently-usable movement tool toward {@code approachTarget}. Unlike {@link #tick}, which
     * trusts the caller's aim completely (RECOVER's stage anchor and EVADE's flee point are
     * already safe by construction), this is aimed straight at the enemy, so it vets the
     * landing first — jumps through {@link JumpSafety#isChaseJumpSafe}, dashes through
     * {@link DashSafety}. A tool that would land in the void is skipped for the next one, or
     * nothing fires and the ordinary Citizens chase carries the tick alone. Shares the same
     * commit window as {@link #tick} — a fired tool still blocks re-firing for its
     * delay_after_ticks, and dash cooldowns rate-limit further, so this needs no extra timer.
     *
     * @return true if a tool fired (informational — the caller doesn't need to react)
     */
    public boolean tryApproach(Hero hero, me.stolyy.heroes.control.BotController controller,
                               LivingEntity approachTarget) {
        if (delayLeft > 0) {
            delayLeft--;
            return false;
        }
        for (MovementAction action : actions) {
            if (fireApproach(hero, controller, action.action(), approachTarget)) {
                delayLeft = action.delayAfterTicks();
                return true;
            }
        }
        return false;
    }

    /** Grounded gap-closing only — mirrors EVADE, not RECOVER's airborne pumping (see {@link #tick}). */
    private boolean fireApproach(Hero hero, me.stolyy.heroes.control.BotController controller,
                                 String action, LivingEntity approachTarget) {
        switch (action) {
            case "jump" -> {
                if (!controller.isOnGround()) return false;
                if (!JumpSafety.isChaseJumpSafe(hero, controller, approachTarget)) return false;
                return hero.getMobility().performJump(hero);
            }
            case "primary" -> {
                return fireAbilityApproach(hero, controller, hero.getPrimaryAbility(), hero::usePrimary, approachTarget);
            }
            case "secondary" -> {
                return fireAbilityApproach(hero, controller, hero.getSecondaryAbility(), hero::useSecondary, approachTarget);
            }
            case "smash" -> {
                return fireAbilityApproach(hero, controller, hero.getSmashAbility(), hero::useSmash, approachTarget);
            }
            default -> {
                // sneak/drop/swap aren't gap-closers — nothing to aim at the target.
                return false;
            }
        }
    }

    private boolean fireAbilityApproach(Hero hero, me.stolyy.heroes.control.BotController controller,
                                        Ability ability, Runnable input, LivingEntity approachTarget) {
        if (ability == null || !ability.canExecute(hero)) return false;
        Vector towardTarget = approachTarget.getLocation().toVector().subtract(controller.getLocation().toVector());
        if (!DashSafety.isLandingSafe(hero, controller, ability, towardTarget)) return false;
        input.run();
        return true;
    }

    private boolean firePassive(Hero hero, AbilityType type, Runnable input) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive.getType() == type && passive.canExecute(hero)) {
                input.run();
                return true;
            }
        }
        return false;
    }
}
