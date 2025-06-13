package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.GameTeam;
import me.stolyy.heroes.game.minigame.TeamSettings;
import me.stolyy.heroes.utility.effects.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TeamSettingsGUI extends GUI {
    private final TeamSettings settings;
    private final PartyModeGUI partyGUI;

    public TeamSettingsGUI(GameTeam team, Player player, PartyModeGUI partyGUI) {
        this.inventory = Bukkit.createInventory(player, 36, team.color().chatColor().toString() + " Team Settings");
        this.settings = team.settings();
        this.player = player;
        this.partyGUI = partyGUI;

        inventoryItems.put(9, Equipment.customItem(30, "3 Lives"));
        inventoryItems.put(11, createItem(Material.APPLE, "Prestige Health: Disabled"));
        inventoryItems.put(13, Equipment.customItem(7, "Ultimates: Enabled"));
        inventoryItems.put(15, createItem(Material.STONE_SWORD, "Friendly Fire: Disabled"));
        inventoryItems.put(17, Equipment.customItem(35, "Random Heroes: Disabled"));
        inventoryItems.put(31,createItem(Material.BARRIER, "Cancel"));

        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, inventoryItems.getOrDefault(i, createItem(FILLER_MATERIAL, " ")));
        }

        openGUI();
    }

    @Override
    public void handleClick(int slot) {
        switch(slot){
            case 9 -> livesClick();
            case 11 -> togglePrestigeHealth();
            case 13 -> toggleUltimates();
            case 15 -> toggleFriendlyFire();
            case 17 -> toggleRandomHeroes();
            case 31 -> partyGUI.openGUI();
        }
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, inventoryItems.getOrDefault(i, createItem(FILLER_MATERIAL, " ")));
        }
    }

    @Override
    public void openGUI() {
        GUIListener.playerGUIMap.put(player, this);
        GUIListener.isReopening.put(player, true);
        player.openInventory(inventory);
        GUIListener.isReopening.put(player, false);
    }

    private void livesClick(){
        int newLives = 3;
        switch (settings.lives()){
            case 1 -> newLives = 2;
            case 3 -> newLives = 4;
            case 4 -> newLives = 1;
        }
        ItemStack newItem = Equipment.customItem(30, newLives + " Lives");
        settings.setLives(newLives);
        inventoryItems.put(9, newItem);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void togglePrestigeHealth(){
        if(settings.maxHealth() == 100) { // enable logic
            settings.setMaxHealth(120);
            inventoryItems.put(11, createItem(Material.ENCHANTED_GOLDEN_APPLE, "Prestige Health: Enabled"));
        } else { // disable logic
            settings.setMaxHealth(100);
            inventoryItems.put(11, createItem(Material.APPLE, "Prestige Health: Disabled"));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void toggleUltimates(){
        settings.setUltimatesEnabled(!settings.ultimatesEnabled());
        if(settings.ultimatesEnabled()) inventoryItems.put(12, Equipment.customItem(7, "Ultimates: Enabled"));
        else inventoryItems.put(13, Equipment.customItem(6, "Ultimates: Disabled"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void toggleFriendlyFire(){
        settings.setFriendlyFire(!settings.friendlyFire());
        if(settings.friendlyFire()) inventoryItems.put(14, createItem(Material.GOLDEN_SWORD, "Friendly Fire: Enabled"));
        else inventoryItems.put(15, createItem(Material.STONE_SWORD, "Friendly Fire: Disabled"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void toggleRandomHeroes(){
        settings.setRandomizeHeroes(!settings.randomizeHeroes());
        if(settings.randomizeHeroes()) inventoryItems.put(16, Equipment.customItem(34, "Random Heroes: Enabled"));
        else inventoryItems.put(17, Equipment.customItem(35, "Random Heroes: Disabled"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }
}
