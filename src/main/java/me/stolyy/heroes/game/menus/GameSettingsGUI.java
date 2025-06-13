package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameSettings;
import me.stolyy.heroes.game.minigame.TeamSettings;
import me.stolyy.heroes.utility.effects.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GameSettingsGUI extends GUI {
    private final Game game;
    private final GameSettings settings;
    private final PartyModeGUI partyGUI;
    private final TeamSettings defaultSettings = new TeamSettings();

    public GameSettingsGUI(Game game, Player player, PartyModeGUI partyGUI) {
        this.inventory = Bukkit.createInventory(player, 54,  "Game Settings");
        this.game = game;
        this.player = player;
        this.settings = game.settings();
        this.partyGUI = partyGUI;
        inventoryItems.put(9, Equipment.customItem(30, "3 Lives"));
        inventoryItems.put(11, createItem(Material.APPLE, "Prestige Health: Disabled"));
        inventoryItems.put(13, Equipment.customItem(7, "Ultimates: Enabled"));
        inventoryItems.put(15, createItem(Material.STONE_SWORD, "Friendly Fire: Disabled"));
        inventoryItems.put(17, Equipment.customItem(35, "Random Heroes: Disabled"));
        inventoryItems.put(29, createItem(Material.CLOCK, "Match Timer: 420"));
        inventoryItems.put(31, createItem(Material.OAK_SIGN, "Change Map"));
        inventoryItems.put(33, Equipment.customItem(2, "Smash Crystals: Disabled"));
        inventoryItems.put(49,createItem(Material.BARRIER, "Cancel"));

        for (int i = 0; i < 54; i++) {
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
            case 29 -> timerClick();
            case 31 -> mapGUI();
            case 33 -> toggleCrystals();
            case 49 -> {
                partyGUI.openGUI();
                game.copyTeamSettingsToAllTeams(defaultSettings);
            }
        }
        for (int i = 0; i < 54; i++) {
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
        switch (defaultSettings.lives()){
            case 1 -> newLives = 2;
            case 3 -> newLives = 4;
            case 4 -> newLives = 1;
        }
        ItemStack newItem = Equipment.customItem(30, newLives + " Lives");
        defaultSettings.setLives(newLives);
        inventoryItems.put(9, newItem);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void togglePrestigeHealth(){
        if(defaultSettings.maxHealth() == 100) { // enable logic
            defaultSettings.setMaxHealth(120);
            inventoryItems.put(11, createItem(Material.ENCHANTED_GOLDEN_APPLE, "Prestige Health: Enabled"));
        } else { // disable logic
            defaultSettings.setMaxHealth(100);
            inventoryItems.put(11, createItem(Material.APPLE, "Prestige Health: Disabled"));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void toggleUltimates(){
        defaultSettings.setUltimatesEnabled(!defaultSettings.ultimatesEnabled());
        if(defaultSettings.ultimatesEnabled()) inventoryItems.put(12, Equipment.customItem(7, "Ultimates: Enabled"));
        else inventoryItems.put(13, Equipment.customItem(6, "Ultimates: Disabled"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void toggleFriendlyFire(){
        defaultSettings.setFriendlyFire(!defaultSettings.friendlyFire());
        if(defaultSettings.friendlyFire()) inventoryItems.put(14, createItem(Material.GOLDEN_SWORD, "Friendly Fire: Enabled"));
        else inventoryItems.put(15, createItem(Material.STONE_SWORD, "Friendly Fire: Disabled"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void toggleRandomHeroes(){
        defaultSettings.setRandomizeHeroes(!defaultSettings.randomizeHeroes());
        if(defaultSettings.randomizeHeroes()) inventoryItems.put(16, Equipment.customItem(34, "Random Heroes: Enabled"));
        else inventoryItems.put(16, Equipment.customItem(35, "Random Heroes: Disabled"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void timerClick(){
        int newTime = 420;
        switch(settings.timer()){
            case 180 -> newTime = 300;
            case 420 -> newTime = 600;
            case 600 -> newTime = 180;
        }
        ItemStack newItem = createItem(Material.CLOCK, "Match Timer: " + newTime);
        settings.setTimer(newTime);
        inventoryItems.put(29, newItem);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void mapGUI(){
        GUIListener.playerGUIMap.put(player, null);
        new GameMapGUI(game, player, partyGUI);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void toggleCrystals(){
        settings.setSmashCrystals(!settings.smashCrystals());
        if(settings.smashCrystals()) inventoryItems.put(33, Equipment.customItem(3, "Smash Crystals: Enabled"));
        else inventoryItems.put(33, Equipment.customItem(2, "Smash Crystals: Disabled"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }
}
