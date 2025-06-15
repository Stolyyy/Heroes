package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;


public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (GUIManager.hasOpenGui(player)) {
            GUIManager.handleClick(event);
        } else if (GameManager.isPlayerInGame(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        GUIManager.handleClose(event);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (GUIManager.hasOpenGui(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
