package me.stolyy.heroes.Game.Menus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;

public class GUIListener implements Listener {
    public static Map<Player,GUI> playerGUIMap = new HashMap<>();

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if(playerGUIMap.get(player) != null){
            playerGUIMap.get(player).openGUI();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GUI gui = playerGUIMap.get(player);
        if(gui != null){
            gui.handleClick(event.getSlot());
        }
    }
}
