package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;

public class GameListener implements Listener {
    private static final Map<Player, Integer> suffocationTicks = new HashMap<>();
    private static final Map<Player, Boolean> isPlayerRespawningMap = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        Player p = e.getPlayer();
        Game game = GameManager.getPlayerGame(p);
        if(game != null && game.isPlayerRestricted(p)) {
            e.setCancelled(true);
        }
        if (isValidGame(game)) {
            if (!game.getMap().getBoundaries().contains(e.getTo().toVector())
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
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player);
        if (isValidGame(game)) {
            Location respawnLocation = game.getFurthestSpawn(player);
            event.setRespawnLocation(respawnLocation);
            game.onDeath(player);
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
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && event.getEntity() instanceof Player victim) {
            Game game = GameManager.getPlayerGame(damager);
            if (game != null && game == GameManager.getPlayerGame(victim)) {
                if (game.getPlayerTeams().get(damager) == game.getPlayerTeams().get(victim)) {
                    event.setCancelled(true);
                }
            }
        }
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
        return game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS;
    }

    public static boolean isRespawning(Player player){
        return isPlayerRespawningMap.getOrDefault(player, false);
    }

    public static void setPlayerRespawning(Player player, boolean bool){
        isPlayerRespawningMap.put(player, bool);
    }
}
