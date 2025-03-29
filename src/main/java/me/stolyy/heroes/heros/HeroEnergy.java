package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class HeroEnergy extends Hero {
    protected double energy;
    protected double maxEnergy;
    protected double energyRegenRate;
    protected boolean canIncreaseEnergy;

    protected void initializeEnergy(double energyRegenRate) {
        updateEnergy(player);
    }

    protected double getEnergy() {
        return energy;
    }

    protected void setEnergy(double energy) {
        this.energy = Math.min(Math.max(energy, 0), maxEnergy);
    }

    protected double getMaxEnergy() {
        return maxEnergy;
    }

    protected double getEnergyRegenRate() {
        return energyRegenRate;
    }

    protected void setEnergyRegenRate(double rate) {
        energyRegenRate = rate;
    }

    protected boolean canIncreaseEnergy() {
        return canIncreaseEnergy;
    }

    protected void setCanIncreaseEnergy(boolean canIncrease) {
        this.canIncreaseEnergy = canIncrease;
    }

    protected void updateEnergy(Player player) {
        new EnergyUpdateTask(player, this).runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    protected void updateXpBar() {
        int xpLevels = (int) getEnergy();
        float xpProgress = (float) (getEnergy() / getMaxEnergy());
        xpProgress = Math.max(0.0f, Math.min(1.0f, xpProgress));
        player.setLevel(xpLevels);
        player.setExp(xpProgress);
    }

    protected void addEnergy(double amount) {
        setEnergy(getEnergy() + amount);
    }

    protected void removeEnergy(double amount) {
        setEnergy(getEnergy() - amount);
    }

    protected boolean hasEnough(double amount) {
        return getEnergy() >= amount;
    }


    protected class EnergyUpdateTask extends BukkitRunnable {
        private Player player;
        private HeroEnergy energy;

        public EnergyUpdateTask(Player player, HeroEnergy energy) {
            this.player = player;
            this.energy = energy;
        }

        @Override
        public void run() {
            if(HeroManager.getHero(player) != energy) {
                this.cancel();
                return;
            }
            if (energy.getEnergy() < energy.getMaxEnergy() && energy.canIncreaseEnergy()) {
                energy.addEnergy(energy.getEnergyRegenRate());
            }
            energy.updateXpBar();
        }
    }

}

