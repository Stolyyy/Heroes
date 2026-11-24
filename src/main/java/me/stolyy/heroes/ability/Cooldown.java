package me.stolyy.heroes.ability;

import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.ModifierStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Cooldown {
    private long baseCooldownDuration; // ms (configured wait time)
    private long lastUsed; // When the cooldown *started* ticking
    private long activeUntil; // ms (when the active phase ends)
    private final ModifierStack durationMods = new ModifierStack();

    private int baseEnergyCost;
    private final ModifierStack energyCostMods = new ModifierStack();
    private int chargesRequired;
    private int chargesCurrent;
    private long lastNotReadyMessage = 0;

    public Cooldown(long cooldownDuration, int energyCost, int chargesRequired) {
        this.baseCooldownDuration = cooldownDuration;
        this.baseEnergyCost = energyCost;
        this.chargesRequired = chargesRequired;
        this.lastUsed = 0;
        this.activeUntil = 0;
        this.chargesCurrent = Math.max(chargesRequired, 0);
    }

    public void start(long activeDurationMs) {
        long now = System.currentTimeMillis();
        // Tick-grid amortization: ability use is sampled on tick boundaries, so a cooldown
        // that isn't a multiple of 50ms always rounded UP (75ms effectively fired every
        // 100ms). Anchoring a rapid-fire restart to the instant the cooldown ACTUALLY came
        // ready — rather than the tick that noticed — carries the sub-tick remainder into
        // the next cycle: 75ms alternates 1-2 ticks, 112ms goes 2,2,3 — exact on average.
        // A restart more than one tick after ready is a fresh press (anchor = now), so
        // pausing can never bank shots.
        long prevReady = lastUsed + effectiveDuration();
        long anchor = (lastUsed > 0 && now >= prevReady && now - prevReady < 50) ? prevReady : now;
        if (activeDurationMs > 0) {
            this.activeUntil = anchor + activeDurationMs;
            // Cooldown starts AFTER active phase
            this.lastUsed = anchor + activeDurationMs;
        } else {
            this.activeUntil = 0;
            this.lastUsed = anchor;
        }

        if (chargesRequired > 0) {
            this.chargesCurrent = chargesRequired;
        }
    }

    public void start() {
        start(0);
    }

    public void forceReady() {
        this.lastUsed = 0;
        this.activeUntil = 0;
        if (chargesRequired > 0) {
            this.chargesCurrent = 0;
        }
    }

    public void reduceCooldown(long ms) {
        // Can only reduce if we are actually waiting on cooldown, not while active
        if (getTimeRemaining() > 0 && !isInUse()) {
            this.lastUsed -= ms;
        }
    }

    /** Pushes the running cooldown further out — miss penalties (SpooderWeb) and the like. */
    public void penalize(long ms) {
        this.lastUsed += ms;
    }

    public boolean isReady(int currentEnergy) {
        if (isInUse()) return false;
        if (getTimeRemaining() > 0) return false;
        if (chargesRequired > 0 && chargesCurrent > 0) return false;
        int cost = getEnergyCost();
        if (cost > 0 && currentEnergy < cost) return false;
        return true;
    }

    public boolean isInUse() {
        return System.currentTimeMillis() < activeUntil;
    }

    // Modifier-scaled wait time — every internal duration read goes through this.
    private long effectiveDuration() {
        return Math.max(0L, Math.round(durationMods.evaluate(baseCooldownDuration, 0.0)));
    }

    public long getTimeRemaining() {
        long effective = effectiveDuration();
        if (effective <= 0) return 0;
        return Math.max(0, (lastUsed + effective) - System.currentTimeMillis());
    }

    public float getVisualProgress(int currentEnergy) {
        if (isInUse()) return 1.0f; // Active

        float timeProg = 0f;
        long effective = effectiveDuration();
        if (effective > 0) {
            timeProg = (float) getTimeRemaining() / effective;
        }

        float chargeProg = 0f;
        if (chargesRequired > 0) {
            chargeProg = (float) chargesCurrent / chargesRequired;
        }

        float energyProg = 0f;
        int cost = getEnergyCost();
        if (cost > 0 && currentEnergy < cost) {
            energyProg = 1.0f - ((float) currentEnergy / cost);
        }

        return Math.max(timeProg, Math.max(chargeProg, energyProg));
    }

    public void reset() {
        this.lastUsed = System.currentTimeMillis();
        this.activeUntil = 0;
        if (chargesRequired > 0) {
            this.chargesCurrent = 0;
        }
    }

    public void charge(int amount) {
        if (chargesRequired > 0) {
            this.chargesCurrent = Math.max(0, chargesCurrent - amount);
        }
    }

    public void addDurationMultiplier(Object key, double mult) { durationMods.putMult(key, mult); }
    public void addDurationFlat(Object key, double deltaMs) { durationMods.putFlat(key, deltaMs); }
    public void removeDurationModifier(Object key) { durationMods.remove(key); }

    public void addEnergyCostMultiplier(Object key, double mult) { energyCostMods.putMult(key, mult); }
    public void addEnergyCostFlat(Object key, double delta) { energyCostMods.putFlat(key, delta); }
    public void removeEnergyCostModifier(Object key) { energyCostMods.remove(key); }

    public void clearModifiers() {
        durationMods.clear();
        energyCostMods.clear();
    }

    public int getEnergyCost() { return (int) Math.max(0L, Math.round(energyCostMods.evaluate(baseEnergyCost, 0.0))); }
    public int getBaseEnergyCost() { return baseEnergyCost; }
    public long getCooldownDuration() { return effectiveDuration(); }
    public long getBaseCooldownDuration() { return baseCooldownDuration; }
    public long getActiveTimeRemaining() { return Math.max(0, activeUntil - System.currentTimeMillis()); }

    public void sendNotReadyVisuals(Hero hero, String abilityName) {
        long now = System.currentTimeMillis();
        if (now - lastNotReadyMessage < 1000) return;
        lastNotReadyMessage = now;

        Controller controller = hero.getController();
        if (controller == null) return;

        int currentEnergy = (int) hero.getEnergy().getCurrentEnergy();
        int cost = getEnergyCost();

        if (isInUse()) {
            controller.sendMessage(Component.text(
                abilityName + " is currently active!",
                NamedTextColor.YELLOW));
        } else if (getTimeRemaining() > 0) {
            double seconds = getTimeRemaining() / 1000.0;
            controller.sendMessage(Component.text(
                abilityName + " is on cooldown! (" + String.format("%.1f", seconds) + "s)",
                NamedTextColor.RED));
        } else if (cost > 0 && currentEnergy < cost) {
            controller.sendMessage(Component.text(
                "Not enough energy to use " + abilityName + "!",
                NamedTextColor.YELLOW));
        } else if (chargesRequired > 0 && chargesCurrent > 0) {
            controller.sendMessage(Component.text(
                abilityName + " needs " + chargesCurrent + " more charges!",
                NamedTextColor.RED));
        }
    }
}
