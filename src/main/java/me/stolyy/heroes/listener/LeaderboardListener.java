package me.stolyy.heroes.listener;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.vfx.ui.visual.LeaderboardDisplay;
import me.stolyy.heroes.vfx.ui.visual.LevelLeaderboardDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Routes player clicks on the lobby leaderboards' Interaction entities into view changes, and
 * applies the per-player visibility filters when a player joins so they only see one stack per
 * board. The elo board toggles between two modes on any click; the level board steps forward on
 * right-click and backward on left-click (Overall + one view per hero is a long cycle).
 */
public class LeaderboardListener implements Listener {

    /** Apply default-view visibility as soon as the player is online. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        LeaderboardDisplay lb = Heroes.getLeaderboardDisplay();
        if (lb != null && lb.isEnabled()) {
            lb.applyVisibility(event.getPlayer());
        }
        LevelLeaderboardDisplay levelLb = Heroes.getLevelLeaderboardDisplay();
        if (levelLb != null && levelLb.isEnabled()) {
            levelLb.applyVisibility(event.getPlayer());
        }
    }

    /** Right-click: elo board toggles; level board steps forward. */
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // off-hand fires too; ignore the duplicate
        LeaderboardDisplay lb = Heroes.getLeaderboardDisplay();
        if (lb != null && lb.isInteractionEntity(event.getRightClicked())) {
            event.setCancelled(true);
            lb.togglePlayerMode(event.getPlayer());
            return;
        }
        LevelLeaderboardDisplay levelLb = Heroes.getLevelLeaderboardDisplay();
        if (levelLb != null && levelLb.isInteractionEntity(event.getRightClicked())) {
            event.setCancelled(true);
            levelLb.cyclePlayerView(event.getPlayer(), true);
        }
    }

    /** Left-click (attack): elo board toggles; level board steps backward. Sneaking resets either board to its default view. */
    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        LeaderboardDisplay lb = Heroes.getLeaderboardDisplay();
        if (lb != null && lb.isInteractionEntity(event.getEntity())) {
            event.setCancelled(true);
            if (player.isSneaking()) lb.resetView(player);
            else lb.togglePlayerMode(player);
            return;
        }
        LevelLeaderboardDisplay levelLb = Heroes.getLevelLeaderboardDisplay();
        if (levelLb != null && levelLb.isInteractionEntity(event.getEntity())) {
            event.setCancelled(true);
            if (player.isSneaking()) levelLb.resetView(player);
            else levelLb.cyclePlayerView(player, false);
        }
    }
}
