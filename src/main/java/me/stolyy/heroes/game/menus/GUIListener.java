package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;

public class GUIListener implements Listener {
    public static Map<Player,GUI> playerGUIMap = new HashMap<>();
    public static Map<Player, Boolean> isReopening = new HashMap<>();

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GUI gui = playerGUIMap.get(player);
        if (gui == null) return;
            // Check if the inventory is being reopened programmatically
        if (isReopening.getOrDefault(player, false)) {
                // Reset the flag and do not reopen
            isReopening.put(player, false);
            return;
        }

        // Set the flag  before reopening to prevent recursion
        isReopening.put(player, true);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            gui.openGUI();
            isReopening.put(player, false);
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GUI gui = playerGUIMap.get(player);
        if(gui != null){
            gui.handleClick(event.getSlot());
            event.setCancelled(true);
        }
    }
}
