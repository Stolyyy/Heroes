package me.stolyy.heroes.game.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

// all heroes in GUI
// description in lore
// click to select
// select bug -> charms
public class HeroSelectGUI extends GUI {

    public HeroSelectGUI(Player player, int size, String title) {
        super(player, size, title);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {

    }

    @Override
    protected void populate() {

    }
}
