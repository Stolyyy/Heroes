package me.stolyy.heroes.characters.mechanics;

import org.bukkit.entity.Player;

public class EnergyMechanic {
    
    private static final int MAX_ENERGY = 100;
    private static final int REGEN_PER_SECOND = 20;

    public void tick(Player player) {
        // Logic to increment player's energy in their PlayerData
        // PlayerData data = PlayerData.get(player);
        // data.setEnergy(Math.min(MAX_ENERGY, data.getEnergy() + (REGEN_PER_SECOND / 20)));
    }

    public boolean tryConsume(Player player, int cost) {
        // PlayerData data = PlayerData.get(player);
        // if (data.getEnergy() >= cost) {
        //     data.setEnergy(data.getEnergy() - cost);
        //     return true;
        // }
        return false;
    }
}