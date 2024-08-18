package me.stolyy.heroes.Games;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerHealthListener implements Listener {
    private final GameManager gameManager;
    private final JavaPlugin plugin;

    public PlayerHealthListener(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            updateHealthDelayed((Player) event.getEntity());
        }
    }

    @EventHandler
    public void onPlayerHeal(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            updateHealthDelayed((Player) event.getEntity());
        }
    }

    private void updateHealthDelayed(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Game game = gameManager.getPlayerGame(player);
            if (game != null) {
                game.updatePlayerListHealth(player);
            }
        }, 1L); // Delay by 1 tick
    }
}