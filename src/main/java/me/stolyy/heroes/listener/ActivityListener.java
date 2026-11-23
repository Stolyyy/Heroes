package me.stolyy.heroes.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.stolyy.heroes.game.queue.AfkTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Feeds {@link AfkTracker}. Every handler runs at MONITOR with ignoreCancelled=false: a cancelled
 * interaction or a blocked command is still a human at the keyboard.
 */
public class ActivityListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        AfkTracker.touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        AfkTracker.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        // Head turns count: a player reading chat while standing still is not AFK. Knockback and
        // falling move the body without the camera, which is why orientation is checked at all.
        if (event.hasChangedOrientation() || event.hasChangedPosition()) {
            AfkTracker.touch(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        AfkTracker.touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        AfkTracker.touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        AfkTracker.touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) AfkTracker.touch(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        AfkTracker.touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        AfkTracker.touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        AfkTracker.touch(event.getPlayer());
    }
}
