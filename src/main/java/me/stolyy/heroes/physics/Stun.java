package me.stolyy.heroes.physics;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Composition helper for "stun"-style status effects: hold a victim in place for a duration,
 * then run an end-of-stun callback. Compose into per-instance ability state.
 *
 * <p>Each tick (when {@link #tick(Hero)} is called by the owning ability), every currently
 * stunned victim has their velocity zeroed. Naturally-expired stuns fire the {@code onEnd}
 * callback; victims that died/despawned during their stun are silently removed without
 * firing it. Use {@link #releaseAll()} to clear all stuns without firing callbacks (for
 * ability-cancellation paths where you don't want the stun "finisher" to run).
 *
 * <p>This helper does not handle visuals — abilities should query
 * {@link #isStunned(LivingEntity)} and render their own per-tick effects.
 */
public class Stun {
    private final long durationMs;
    private final BiConsumer<Hero, LivingEntity> onEnd;
    private final Map<UUID, Long> startTimes = new HashMap<>();

    /**
     * @param durationMs how long the stun lasts before {@code onEnd} fires
     * @param onEnd      callback invoked when a stun expires naturally on a still-alive victim;
     *                   args are (the {@code attacker} passed to {@link #tick(Hero)}, the victim)
     */
    public Stun(long durationMs, BiConsumer<Hero, LivingEntity> onEnd) {
        this.durationMs = durationMs;
        this.onEnd = onEnd;
    }

    /**
     * Begin (or restart) a stun on {@code victim}. Calling on an already-stunned victim
     * resets the timer to now.
     */
    public void apply(LivingEntity victim) {
        startTimes.put(victim.getUniqueId(), System.currentTimeMillis());
        Interactions.applyVelocity(victim, new Vector(0, 0, 0));
    }

    public boolean isStunned(LivingEntity victim) {
        return victim != null && startTimes.containsKey(victim.getUniqueId());
    }

    /**
     * Per-tick maintenance. Zeroes velocity for active stuns, fires {@code onEnd} for victims
     * whose duration has elapsed (only if still alive), and silently drops entries for victims
     * who died during their stun.
     */
    public void tick(Hero attacker) {
        if (startTimes.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = startTimes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            LivingEntity victim = (LivingEntity) Bukkit.getEntity(entry.getKey());

            if (victim == null || victim.isDead()) {
                it.remove();
                continue;
            }

            if (now - entry.getValue() >= durationMs) {
                it.remove();
                onEnd.accept(attacker, victim);
            } else {
                Interactions.applyVelocity(victim, new Vector(0, 0, 0));
            }
        }
    }

    /** Clears all stuns without firing {@code onEnd}. Use for ability cancellation. */
    public void releaseAll() {
        startTimes.clear();
    }
}
