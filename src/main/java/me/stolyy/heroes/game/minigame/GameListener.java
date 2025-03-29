package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

public class GameListener implements Listener {
    private static Map<Player, Integer> suffocationTicks = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        Player p = e.getPlayer();
        Game game = GameManager.getPlayerGame(p);
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
        Game game = GameManager.getPlayerGame(p);
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
            game.onDeath(p);
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
                //!game.getSettings().isFriendlyFire() &&
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
}
