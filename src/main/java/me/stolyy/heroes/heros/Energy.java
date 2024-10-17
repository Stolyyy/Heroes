package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public interface Energy {
    Map<UUID, EnergyData> energyMap = new WeakHashMap<>();

    class EnergyData {
        double energy;
        double maxEnergy;
        double energyRegenRate;
        boolean canIncreaseEnergy;

        EnergyData(double energyRegenRate) {
            this.energy = maxEnergy;
            this.maxEnergy = 100;
            this.energyRegenRate = energyRegenRate;
            this.canIncreaseEnergy = true;
        }
    }

    default void initializeEnergy(Player player, double energyRegenRate) {
        energyMap.put(player.getUniqueId(), new EnergyData(energyRegenRate));
        updateEnergy(player);
    }

    default void removeEnergyData(Player player){
        energyMap.remove(player.getUniqueId());
    }

    default double getEnergy(Player player) {
        return energyMap.get(player.getUniqueId()).energy;
    }

    default void setEnergy(Player player, double energy) {
        EnergyData data = energyMap.get(player.getUniqueId());
        data.energy = Math.min(Math.max(energy, 0), data.maxEnergy);
    }

    default double getMaxEnergy(Player player) {
        return energyMap.get(player.getUniqueId()).maxEnergy;
    }

    default double getEnergyRegenRate(Player player) {
        return energyMap.get(player.getUniqueId()).energyRegenRate;
    }

    default void setEnergyRegenRate(Player player, double rate) {
        energyMap.get(player.getUniqueId()).energyRegenRate = rate;
    }

    default boolean canIncreaseEnergy(Player player) {
        return energyMap.get(player.getUniqueId()).canIncreaseEnergy;
    }

    default void setCanIncreaseEnergy(Player player, boolean canIncrease) {
        energyMap.get(player.getUniqueId()).canIncreaseEnergy = canIncrease;
    }

    default void updateEnergy(Player player) {
        new EnergyUpdateTask(player, this).runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    default void updateXpBar(Player player) {
        int xpLevels = (int) getEnergy(player);
        float xpProgress = (float) (getEnergy(player) / getMaxEnergy(player));
        xpProgress = Math.max(0.0f, Math.min(1.0f, xpProgress));
        player.setLevel(xpLevels);
        player.setExp(xpProgress);
    }

    default void addEnergy(Player player, double amount) {
        setEnergy(player, getEnergy(player) + amount);
    }

    default void removeEnergy(Player player, double amount) {
        setEnergy(player, getEnergy(player) - amount);
    }

    default boolean hasEnough(Player player, double amount) {
        return getEnergy(player) >= amount;
    }

    class EnergyUpdateTask extends BukkitRunnable {
        private final Player player;
        private final Energy energy;

        public EnergyUpdateTask(Player player, Energy energy) {
            this.player = player;
            this.energy = energy;
        }

        @Override
        public void run() {
            if(!energyMap.containsKey(player.getUniqueId())) {
                this.cancel();
                return;
            }
            if (energy.getEnergy(player) < energy.getMaxEnergy(player) && energy.canIncreaseEnergy(player)) {
                energy.addEnergy(player, energy.getEnergyRegenRate(player));
            }
            energy.updateXpBar(player);
        }
    }
}