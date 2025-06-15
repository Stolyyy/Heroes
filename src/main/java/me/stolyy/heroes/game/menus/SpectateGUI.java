package me.stolyy.heroes.game.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

// Get lives leader for head
// display all players in lore, spectators too
// click to spectate player
public class SpectateGUI extends GUI{

    public SpectateGUI(Player player, int size, String title) {
        super(player, size, title);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {

    }

    @Override
    protected void populate() {

    }
}
