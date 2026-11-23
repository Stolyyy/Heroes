package me.stolyy.heroes.ability;

import me.stolyy.heroes.vfx.effect.SoundEffect;

public class Ammo {
    public enum Mode {
        CLIP,  // Standard reload when empty
        STOCK, // Individual charges regen over time
        POWER  // Gathers charges, execute consumes ALL to scale strength
    }

    private final Mode mode;
    private int max;
    private int current;
    private int lastConsumed;
    private final long regenDuration;
    private long lastRegenTime;
    private SoundEffect reloadSound;

    public Ammo(Mode mode, int max, long regenDuration, SoundEffect reloadSound) {
        this.mode = mode;
        this.max = max;
        this.current = (mode == Mode.POWER) ? 0 : max;
        this.regenDuration = regenDuration;
        this.lastRegenTime = System.currentTimeMillis();
        this.reloadSound = reloadSound;
    }

    public SoundEffect getReloadSound() { return reloadSound; }

    public boolean tryConsume() {
        if (!canConsume()) {
            lastConsumed = 0;
            return false;
        }

        if (mode == Mode.POWER) {
            lastConsumed = current;
            current = 0;
            lastRegenTime = System.currentTimeMillis();
            return true;
        }

        lastConsumed = 1;
        current--;
        
        if (mode == Mode.CLIP && current == 0) {
            lastRegenTime = System.currentTimeMillis();
        } else if (mode == Mode.STOCK && current == max - 1) {
            lastRegenTime = System.currentTimeMillis();
        }
        return true;
    }

    public boolean canConsume(){
        return current > 0;
    }

    /**
     * Empties all charges and restarts the regen timer. Used at game start for STOCK
     * smash abilities — ults must charge up from zero, not open the game banked.
     */
    public void drain() {
        current = 0;
        lastRegenTime = System.currentTimeMillis();
    }

    /**
     * Fills to max and restarts the regen timer. Unlike {@link #forceReload()} this deliberately
     * applies to an already-empty clip: it exists for weapons that come up loaded regardless of
     * what state they were stowed in (Punishgers' swap). Restamping matters as much as the fill —
     * an untouched {@code lastRegenTime} would let wall-clock accrued while the weapon was stowed
     * carry over into the next reload.
     */
    public void refill() {
        current = max;
        lastRegenTime = System.currentTimeMillis();
    }

    /** 0..1 progress toward the next STOCK charge; 1 when full (or not STOCK). */
    public double getChargeProgress() {
        if (mode != Mode.STOCK || current >= max || regenDuration <= 0) return 1.0;
        return Math.min(1.0, (System.currentTimeMillis() - lastRegenTime) / (double) regenDuration);
    }

    /** True while a CLIP is empty and its auto-reload countdown is running. */
    public boolean isReloading() {
        return mode == Mode.CLIP && current == 0;
    }

    /** Ms until the CLIP auto-reload completes; 0 when not reloading. */
    public long getReloadRemainingMs() {
        if (!isReloading()) return 0;
        return Math.max(0, regenDuration - (System.currentTimeMillis() - lastRegenTime));
    }

    /** 0..1 fraction of the CLIP reload completed; 1 when not reloading. */
    public double getReloadProgress() {
        if (!isReloading() || regenDuration <= 0) return 1.0;
        return Math.min(1.0, (System.currentTimeMillis() - lastRegenTime) / (double) regenDuration);
    }

    /**
     * Instant manual reload (Q). CLIP mode only, and only for a partially spent clip:
     * full clips and in-progress auto-reloads (current == 0) are untouched.
     */
    public void forceReload() {
        if (mode != Mode.CLIP || current <= 0 || current >= max) return;
        current = max;
        lastRegenTime = System.currentTimeMillis();
    }

    /**
     * Manual reload (Q): dumps the remaining clip and starts the timed auto-reload —
     * the clip refills after {@code regenDuration} like any empty clip. CLIP mode only;
     * full clips and in-progress reloads are untouched.
     *
     * @return true when a reload actually started — the caller plays the reload sound then
     */
    public boolean startReload() {
        if (mode != Mode.CLIP || current <= 0 || current >= max) return false;
        current = 0;
        lastRegenTime = System.currentTimeMillis();
        return true;
    }

    public void tick() {
        if (mode == Mode.POWER) return;
        if (current >= max) return;
        if (mode == Mode.CLIP && current > 0) return;

        long now = System.currentTimeMillis();
        if (now - lastRegenTime >= regenDuration) {
            if (mode == Mode.CLIP) {
                current = max;
            } else {
                current++;
            }
            lastRegenTime = now;
        }
    }

    public int getLastConsumed() { return lastConsumed; }
    public Mode getMode() { return mode; }
    public int getCurrent() { return current; }
    public int getMax() { return max; }
    public boolean isReady() { return current > 0; }

    public void addAmmo(int amount) {
        this.current = Math.min(max, Math.max(0, current + amount));
    }

    public void setAmmo(int amount) {
        this.current = Math.min(max, Math.max(0, amount));
    }

    public void setMax(int max) {
        this.max = max;
    }
}
