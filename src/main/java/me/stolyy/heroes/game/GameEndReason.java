package me.stolyy.heroes.game;

public enum GameEndReason {
    TIMEOUT,
    LAST_TEAM_STANDING,
    KILL_TARGET,
    HERO_EXHAUSTION,
    FORFEIT,
    /**
     * Admin force-end (/forceend). The round is discarded: no winner is discovered, no win
     * sound/visuals or summaries are sent, and nothing is written to stats, XP or elo — a
     * cancelled game must not rate the players who were in it. Teardown is otherwise identical.
     */
    CANCELLED
}
