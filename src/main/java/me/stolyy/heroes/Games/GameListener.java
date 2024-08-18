package me.stolyy.heroes.Games;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {
    private final Heroes plugin;
    private final GameManager gameManager;
    private final Map<UUID, ItemStack> selectedHeads = new HashMap<>();

    public GameListener(Heroes plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);
        if (game != null) {
            game.removePlayer(player);
            gameManager.leaveGame(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            Game game = gameManager.getPlayerGame(damager);
            if (game != null && game == gameManager.getPlayerGame(victim)) {
                if (!game.isFriendlyFire() && game.getPlayerTeam(damager) == game.getPlayerTeam(victim)) {
                    event.setCancelled(true);
                }
                // You can add additional game-specific damage handling here
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Game game = gameManager.getPlayerGame(player);
        if (game == null) return;

        if (event.getView().getTitle().equals("Party Game Setup")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
                selectedHeads.put(player.getUniqueId(), clickedItem);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            } else if (selectedHeads.containsKey(player.getUniqueId())) {
                ItemStack selectedHead = selectedHeads.remove(player.getUniqueId());
                game.getGameGUI().handlePlayerMove(player, selectedHead, slot);
            } else {
                game.getGameGUI().handleInventoryClick(player, slot);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Game game = gameManager.getPlayerGame(event.getPlayer());
        if (game != null && game.isPlayerFrozen(event.getPlayer())) {
            // Cancel the event if the player has actually moved
            if (event.getTo().getX() != event.getFrom().getX() ||
                    event.getTo().getY() != event.getFrom().getY() ||
                    event.getTo().getZ() != event.getFrom().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Game game = gameManager.getPlayerGame(event.getPlayer());
        if (game != null && game.isPlayerFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Game game = gameManager.getPlayerGame(event.getPlayer());
        if (game != null && game.isPlayerFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Game game = gameManager.getPlayerGame(player);
            if (game != null && game.getGameMode() == GameEnums.GameMode.PARTY && game.getGameState() == GameEnums.GameState.WAITING) {
                game.playerClosedGUI(player);
                // Reopen the GUI after a short delay, unless the player used the cancel button
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
                    if (game.getPlayers().containsKey(player)) {
                        game.reopenGUIForPlayer(player);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Game game = gameManager.getPlayerGame(player);
            if (game != null && game.getGameMode() == GameEnums.GameMode.PARTY && game.getGameState() == GameEnums.GameState.WAITING) {
                game.playerOpenedGUI(player);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Game game = gameManager.getPlayerGame(player);
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            game.playerDied(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            if (game.getPlayerLives(player) > 0) {
                event.setRespawnLocation(game.getSpawnPoints().get(game.getPlayerTeam(player)));
            } else {
                // Player is eliminated, set them as a spectator
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                event.setRespawnLocation(game.getSpectatorLocation());
            }
        }
    }
}