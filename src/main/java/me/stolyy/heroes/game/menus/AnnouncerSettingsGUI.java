package me.stolyy.heroes.game.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

//Later on, add options for skins, cosmetics, etc, all toggleable in a GUI
public class AnnouncerSettingsGUI extends GUI{

    public AnnouncerSettingsGUI(Player player, int size, String title) {
        super(player, size, title);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {

    }

    @Override
    protected void populate() {

    }
}
