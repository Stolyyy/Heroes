package me.stolyy.heroes.listener;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.party.PartyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerLeaveListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        QueueManager.leaveQueue(player);
        GUIManager.closeAndCleanup(player);
        PartyManager.leaveParty(player);
        // Hero teardown happens inside GameManager.detach -> Game.removePlayer -> HeroManager.removePlayer.
        GameManager.detach(player);
        PlayerStatsManager.unload(player.getUniqueId());
    }
}