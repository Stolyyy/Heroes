package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.util.ModifierStack;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class Energy {
    private int maxEnergy;
    private float baseRegenPerTick;
    private boolean baseCanRegen;
    private float currentEnergy;

    private final ModifierStack regenMods = new ModifierStack();
    private final LinkedHashMap<Object, Float> regenOverrides = new LinkedHashMap<>();
    private final Set<Object> regenBlockers = new LinkedHashSet<>();

    public Energy(HeroStats.EnergyStats stats) {
        apply(stats);
        this.currentEnergy = stats.startingEnergy();
    }

    /** Updates base energy parameters without discarding active keyed modifiers, overrides, or blockers. */
    public void apply(HeroStats.EnergyStats stats) {
        this.maxEnergy = stats.maxEnergy();
        this.baseRegenPerTick = (float) stats.energyPerTick();
        this.baseCanRegen = stats.canIncreaseEnergy();
    }

    public void tick(Controller controller) {
        if (canRegen()) {
            changeEnergy(getRegenPerTick());
        }
        updateDisplay(controller);
    }

    public boolean tryConsume(Controller controller, float cost) {
        if (currentEnergy >= cost) {
            changeEnergy(-cost);
            updateDisplay(controller);
            return true;
        }
        return false;
    }

    public void changeEnergy(float amount) {
        currentEnergy = Math.min(maxEnergy, Math.max(0, currentEnergy + amount));
    }

    public void setEnergy(float amount) {
        currentEnergy = Math.min(maxEnergy, Math.max(0, amount));
    }

    public boolean canRegen() {
        return baseCanRegen && regenBlockers.isEmpty();
    }

    public void blockRegen(Object key) {
        regenBlockers.add(key);
    }

    public void unblockRegen(Object key) {
        regenBlockers.remove(key);
    }

    public void addRegenMultiplier(Object key, double mult) {
        regenMods.putMult(key, mult);
    }

    public void addRegenFlat(Object key, double delta) {
        regenMods.putFlat(key, delta);
    }

    public void removeRegenModifier(Object key) {
        regenMods.remove(key);
    }

    /** Sets a temporary absolute regen override; re-inserts so the most recently applied override wins. */
    public void setRegenOverride(Object key, float regenPerTick) {
        regenOverrides.remove(key);
        regenOverrides.put(key, regenPerTick);
    }

    public void removeRegenOverride(Object key) {
        regenOverrides.remove(key);
    }

    public void clearModifiers() {
        regenMods.clear();
        regenOverrides.clear();
        regenBlockers.clear();
    }

    public float getCurrentEnergy() {
        return currentEnergy;
    }

    public int getMaxEnergy() { return maxEnergy; }

    public float getRegenPerTick() {
        if (!regenOverrides.isEmpty()) {
            float latest = baseRegenPerTick;
            for (float v : regenOverrides.values()) {
                latest = v;
            }
            return latest;
        }
        return (float) regenMods.evaluate(baseRegenPerTick);
    }

    private void updateDisplay(Controller controller) {
        if (maxEnergy > 0) {
            controller.updateEnergyDisplay(currentEnergy / (float) maxEnergy, (int) currentEnergy);
        } else {
            controller.updateEnergyDisplay(0, 0);
        }
    }
}