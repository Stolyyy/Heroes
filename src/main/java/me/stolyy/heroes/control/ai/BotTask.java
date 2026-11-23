package me.stolyy.heroes.control.ai;

/**
 * What a bot currently thinks it is doing. Evaluated as a priority ladder at the top of
 * {@link BaseBotAI#tick}: the highest satisfied condition OWNS the tick — a recovering bot
 * does not navigate to targets, take combat actions, or run strategy movement. Future
 * behaviors (retreat, regroup...) are one condition + one handler each, slotted by priority.
 */
public enum BotTask {
    /** Off the stage over the void: spend jumps and movement abilities to get back. */
    RECOVER,
    /** A MELEE/HYBRID threat inside panic_distance: flee-path away and burn movement tools.
     *  Combat CONTINUES while fleeing (minus ability slots reserved as movement tools) — a
     *  silent retreat handed the chaser free pressure. Exits at 1.3x panic_distance
     *  (hysteresis), or falls through to FIGHT when cornered against the void. */
    EVADE,
    /** Has a valid target: the classic navigate/combat/strategy-movement loop. */
    FIGHT,
    /** Nothing to do: stand down. */
    IDLE
}
