package me.stolyy.heroes.game.minigame;

import io.papermc.paper.event.player.PlayerPickItemEvent;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GameListener implements Listener {
    private static final Map<Player, Integer> suffocationTicks = new HashMap<>();
    private static final Set<Player> respawningPlayers = new HashSet<>();

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event){
        Player player = event.getPlayer();
        if(GameManager.isPlayerInGame(player)){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemPickup(PlayerPickItemEvent event){
        Player player = event.getPlayer();
        if(GameManager.isPlayerInGame(player)){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        Player p = e.getPlayer();
        Game game = GameManager.getPlayerGame(p);
        if(game != null && GameEffects.isRestricted(p)) {
            e.setCancelled(true);
        }
        if (isValidGame(game)) {
            if (!game.mapBounds().contains(e.getTo().toVector())
            && !isRespawning(p)) {
                p.setHealth(0); //kill player
                p.sendMessage("You went out of bounds!");
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Game game = GameManager.getPlayerGame(p);
        if (isValidGame(game)) {
            setPlayerRespawning(p, true);
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> setPlayerRespawning(p, false), 20 * Game.RESPAWN_TIME);
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void processPlayerDeath(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player);
        if (isValidGame(game)) {
            Location respawnLocation = game.furthestSpawn(player);
            event.setRespawnLocation(respawnLocation);
            game.playerDeath(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player);
        if (game != null) {
            game.removePlayer(player);
            GameManager.leaveGame(player);
        }
        suffocationTicks.remove(player);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION && event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            suffocationTicks.put(player,suffocationTicks.getOrDefault(player, 0) + 1);
            if(suffocationTicks.get(player) > 19){
                Bukkit.dispatchCommand(player, "stuck");
                suffocationTicks.put(player,0);
            }
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
                suffocationTicks.put(player, Math.max(suffocationTicks.getOrDefault(player, 0) - 1, 0));
            }, 20L);
        }
    }

    private static boolean isValidGame(Game game){
        return game != null && game.gameState() == GameEnums.GameState.IN_PROGRESS;
    }

    public static boolean isRespawning(Player player){
        return respawningPlayers.contains(player);
    }

    public static void setPlayerRespawning(Player player, boolean bool){
        if(bool) respawningPlayers.add(player);
        else respawningPlayers.remove(player);
    }
}
