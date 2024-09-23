package me.stolyy.heroes.Game;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GameListener {
    private final me.stolyy.heroes.Game.GameManager gameManager;
    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        Player p = e.getPlayer();
        Game game = gameManager.getPlayerGame(p);
        if(game != null && game.isPlayerRestricted(p)) {
            e.setCancelled(true);
        }
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            if (!game.getMap().getBoundaries().contains(e.getTo().toVector())) {
                p.setHealth(0); //kill player
                p.sendMessage("You went out of bounds!");
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Game game = gameManager.getPlayerGame(p);
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
            game.onDeath(p);
        }
    }
}
