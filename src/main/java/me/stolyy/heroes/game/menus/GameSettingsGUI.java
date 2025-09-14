package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.legacy.Equipment;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GameSettingsGUI extends AbstractTeamSettingsGUI {
    private final Game game;

    public GameSettingsGUI(Game game, Player player, PartyModeGUI partyGUI) {
        super(player, 54, "Game Settings", game.defaultTeamSettings(), partyGUI);
        this.game = game;
        update();
    }

    @Override
    protected void populate() {
        inventoryItems.put(49,createItem(Material.BARRIER, "Save & Close"));
    }

    @Override
    protected void update() {
        inventoryItems.put(28, game.settings().smashCrystals() ? Equipment.customItem(3, "Smash Crystals: Enabled") : Equipment.customItem(2, "Smash Crystals: Disabled"));
        inventoryItems.put(30, createItem(Material.OAK_SIGN, "Change Map"));
        inventoryItems.put(32, game.settings().killWalls() ? createItem(Material.RED_SANDSTONE_WALL, "Kill Walls: Enabled") : createItem(Material.SANDSTONE_WALL, "Kill Walls: Disabled"));
        inventoryItems.put(34, createItem(Material.CLOCK, "Match Timer: " + game.settings().timer()));
        super.update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case 9 -> handleLivesClick();
            case 11 -> handlePrestigeHealthClick();
            case 13 -> handleUltimatesClick();
            case 15 -> handleFriendlyFireClick();
            case 17 -> handleRandomHeroesClick();
            case 28 -> handleCrystalsClick();
            case 30 -> handleMapClick();
            case 32 -> handleWallsClick();
            case 34 -> handleTimerClick();
            case 49 -> {
                game.setAllTeamSettings(this.settings);
                GUIManager.open(player, partyGUI);
            }
        }
    }

    private void handleCrystalsClick() {
        game.settings().setSmashCrystals(!game.settings().smashCrystals());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }

    private void handleMapClick() {
        GUIManager.open(player, new GameMapGUI(game, player, partyGUI));
    }

    private void handleWallsClick() {
        game.settings().toggleKillWalls();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }

    private void handleTimerClick() {
        int newTime = switch (game.settings().timer()) {
            case 180 -> 300;
            case 300 -> 420;
            case 420 -> 600;
            case 600 -> 180;
            default -> 420;
        };
        game.settings().setTimer(newTime);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }
}
