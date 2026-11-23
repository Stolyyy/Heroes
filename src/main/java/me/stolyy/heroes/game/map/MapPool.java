package me.stolyy.heroes.game.map;

/**
 * Preset map-pool selector (gamemodes.yml {@code <preset>.map-pool}). Selects WHICH maps a
 * preset draws from by referencing the existing per-GameMode pools rather than naming maps:
 * ONE_V_ONE / TWO_V_TWO = that mode's pool, ANY = union of both, BOTH = intersection of both,
 * ALL = every template map. Resolved via {@link GameMapManager#getMapsForPool}.
 */
public enum MapPool {
    ONE_V_ONE,
    TWO_V_TWO,
    ANY,
    BOTH,
    ALL
}
