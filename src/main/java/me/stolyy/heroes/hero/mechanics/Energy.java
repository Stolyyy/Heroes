package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.control.Controller;

public class Energy {
    private int maxEnergy;
    private float regenPerTick;
    private boolean canRegen;
    private float currentEnergy;

    public Energy(HeroStats.EnergyStats stats) {
        apply(stats);
        this.currentEnergy = stats.startingEnergy();
    }

    public void apply(HeroStats.EnergyStats stats) {
        this.maxEnergy = stats.maxEnergy();
        this.regenPerTick = (float) stats.energyPerTick();
        this.canRegen = stats.canIncreaseEnergy();
    }

    public void tick(Controller controller) {
        if (canRegen) {
            changeEnergy(regenPerTick);
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

    public void setCanRegen(boolean canRegen) {
        this.canRegen = canRegen;
    }

    public void setRegenPerTick(float regenPerTick) {
        this.regenPerTick = regenPerTick;
    }

    public float getCurrentEnergy() {
        return currentEnergy;
    }

    public int getMaxEnergy() { return maxEnergy; }

    public float getRegenPerTick() { return regenPerTick; }

    private void updateDisplay(Controller controller) {
        if (maxEnergy > 0) {
            controller.updateEnergyDisplay(currentEnergy / (float) maxEnergy, (int) currentEnergy);
        } else {
            controller.updateEnergyDisplay(0, 0);
        }
    }
}