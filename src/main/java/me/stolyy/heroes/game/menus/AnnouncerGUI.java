package me.stolyy.heroes.game.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

//GUI with options to joinGame 1v1, 2v2, and party
//Change Hero
//also has a settings option which will open AnnouncerSettings (later)
public class AnnouncerGUI extends GUI {
    public AnnouncerGUI(Player player, int size, String title) {
        super(player, size, title);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {

    }

    @Override
    protected void populate() {

    }

}
