package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.GameTeam;
import me.stolyy.heroes.game.minigame.TeamSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TeamSettingsGUI extends GUI {
    private final TeamSettings settings;
    private final PartyModeGUI partyGUI;

    public TeamSettingsGUI(GameTeam team, Player player, PartyModeGUI partyGUI) {
        this.inventory = Bukkit.createInventory(player, 36, team.color().chatColor().toString() + " Team Settings");
        this.settings = team.settings();
        this.player = player;
        this.partyGUI = partyGUI;
    }

    @Override
    public void handleClick(int slot) {

    }

    @Override
    public void openGUI() {

    }
}
