package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.GameTeam;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class TeamSettingsGUI extends AbstractTeamSettingsGUI {
    public TeamSettingsGUI(GameTeam team, Player player, PartyModeGUI partyGUI) {
        super(player, 36, team.color().chatColor().toString() + " Team Settings", team.settings(), partyGUI);
        update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case 9 -> handleLivesClick();
            case 11 -> handlePrestigeHealthClick();
            case 13 -> handleUltimatesClick();
            case 15 -> handleFriendlyFireClick();
            case 17 -> handleRandomHeroesClick();
            case 31 -> GUIManager.open(player, partyGUI);
        }
    }

    @Override
    protected void populate() {
        inventoryItems.put(31,createItem(Material.BARRIER, "Save & Close"));
    }
}
