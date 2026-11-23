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
        // Hero teardown happens inside GameManager.leaveGame -> Game.removePlayer ->
        // HeroManager.removePlayer. A second direct call here would double-fire the
        // onDeath/onOwnerLost teardown hooks (lobby players have no hero to remove).
        GameManager.leaveGame(player, false);
        PlayerStatsManager.unload(player.getUniqueId());
    }
}