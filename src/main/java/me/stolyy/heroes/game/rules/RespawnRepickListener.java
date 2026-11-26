package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Right-clicking the tagged "Change Hero" item (given for the TDM respawn countdown by
 * {@link RespawnAction#OPEN_HERO_SELECT}) reopens the re-pick GUI — closing it otherwise
 * left no way back in.
 */
public class RespawnRepickListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!RespawnAction.isRepickItem(event.getItem())) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        Game game = GameManager.getPlayerGame(p);
        if (game == null || !game.isRespawning(p.getUniqueId())) return;
        if (game.modeConfig().onRespawn() != RespawnAction.OPEN_HERO_SELECT) return;

        RespawnAction.openPickGui(p, game);
    }
}
