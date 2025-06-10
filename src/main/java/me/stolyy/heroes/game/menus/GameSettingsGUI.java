package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.GameSettings;
import me.stolyy.heroes.game.minigame.TeamSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

public class GameSettingsGUI extends GUI {
    private final GameSettings settings;
    private final PartyModeGUI partyGUI;
    private final TeamSettings defaultSettings = new TeamSettings();

    public GameSettingsGUI(GameSettings settings, Player player, PartyModeGUI partyGUI) {
        this.inventory = Bukkit.createInventory(player, 54,  "Game Settings");
        this.settings = settings;
        this.partyGUI = partyGUI;
    }

    @Override
    public void handleClick(int slot) {

    }

    public void update(){

    }

    @Override
    public void openGUI() {

    }
}
