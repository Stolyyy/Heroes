package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import org.bukkit.entity.LivingEntity;

public interface Strategy {
    /** The spacing ring (V2 §6): BaseBotAI continuously walks toward the point at this
     *  distance from the target, so the bot repositions constantly instead of standing. */
    double getPreferredDistance();

    /** EVADE trigger (V2 §9): a MELEE/HYBRID enemy inside this range panics the bot into
     *  fleeing + burning movement tools. 0 = never. Only Defensive and MidRange read a yml
     *  key ({@code panic_distance}); Aggressive never panics. */
    default double getPanicDistance() {
        return 0;
    }

    boolean shouldUseAbility(Hero hero, AbilityType type, LivingEntity target, double distance);

    /** Ult gate: minimum TARGET hp fraction (0..1) before the bot will spend its smash on
     *  them — a near-dead target is often better finished with a cheap jab than a telegraphed
     *  ult. 0 = no minimum (default: always eligible). Yml key {@code ult_min_target_hp_percent}. */
    default double getUltMinTargetHpPercent() {
        return 0.0;
    }

    /** Ult gate: minimum SELF hp fraction (0..1) before the bot will commit to its smash —
     *  below this it holds the ult rather than risk dying mid-cast, UNLESS it is on its last
     *  life (BaseBotAI.shouldAttemptUlt), where there is no future ult to save by holding.
     *  0 = no minimum (default: always eligible). Yml key {@code ult_min_self_hp_percent}. */
    default double getUltMinSelfHpPercent() {
        return 0.0;
    }

    /** Horizontal distance beyond which FIGHT navigation treats the target as "far": the
     *  chase-jump Case 2 in Aggressive/MidRange, and BaseBotAI.performNavigation's movement-
     *  ability gap-closer, both fire past this. Yml key {@code far_distance}. */
    default double getFarDistance() {
        return 15.0;
    }

    boolean canDoubleJump();

    /**
     * Handles movement logic including double jumps.
     * Double jump cases:
     * 1. Bot is above void - jump toward nearest block
     * 2. Target is above and higher than normal jump reach
     * 3. Target is very far away
     * @return true if a jump was performed, false otherwise
     */
    boolean performMovement(Hero hero, BotController controller, LivingEntity target);
}
