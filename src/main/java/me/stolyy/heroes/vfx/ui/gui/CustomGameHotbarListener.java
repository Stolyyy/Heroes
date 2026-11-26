package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Right-click handling for the {@link CustomGameHotbar} items. */
public class CustomGameHotbarListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        CustomGameHotbar.Action action = CustomGameHotbar.actionOf(event.getItem());
        if (action == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        CustomGameGUI gui = CustomGameHotbar.guiOf(player);
        if (gui == null || GameManager.getPlayerGame(player) != gui.getGame()
                || gui.getGame().gameState() != GameState.WAITING) {
            CustomGameHotbar.remove(player); // stale item: the game it belonged to is gone or started
            return;
        }

        switch (action) {
            case REOPEN -> GUIManager.open(player, gui);
            case START -> gui.startGame();
            case CANCEL -> gui.cancelGame();
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (CustomGameHotbar.actionOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        CustomGameHotbar.remove(event.getPlayer());
    }
}
