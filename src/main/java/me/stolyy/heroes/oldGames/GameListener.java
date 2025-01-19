package me.stolyy.heroes.oldGames;

import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GameListener implements Listener {
    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
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
                if (!game.getSettings().isFriendlyFire() && game.getPlayerTeam(damager) == game.getPlayerTeam(victim)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Game game = gameManager.getPlayerGame(player);
        if (game == null) return;

        String inventoryTitle = event.getView().getTitle();
        int slot = event.getRawSlot();

        event.setCancelled(true); // Cancel all clicks by default

        if (inventoryTitle.equals("PartyC Game Setup")) {
            game.getGameGUI().handleClick(player, slot);
        } else if (inventoryTitle.equals("Game Settings")) {
            new GameSettingsGUI(game).handleClick(player, slot);
        } else if (inventoryTitle.equals("Select GameMap")) {
            new MapGUI(game).handleClick(player, slot);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Game game = gameManager.getPlayerGame(player);
        if (game == null) return;

        if (event.getView().getTitle().equals("Party Game Setup") && game.getGameState() == GameEnums.GameState.WAITING) {
            // Only reopen for the party leader
            if (player.getUniqueId().equals(Heroes.getInstance().getPartyManager().getPartyByPlayer(player.getUniqueId()).getLeader())) {
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
                    if (game.getPlayers().containsKey(player.getUniqueId())) {
                        game.getGameGUI().openInventory(player);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);
        if (game != null) {
            if (game.getGameState() == GameEnums.GameState.WAITING) {
                // Cancel movement during waiting period
                event.setCancelled(true);
            } else if (game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
                game.checkPlayerPosition(player);
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
            game.playerDeath(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            if (game.getPlayerLives(player) > 0) {
                event.setRespawnLocation(game.getCurrentMap().getRandomSpawnPoint(game.getPlayerTeam(player)));
                player.setGameMode(GameMode.ADVENTURE);
            } else {
                player.setGameMode(GameMode.SPECTATOR);
                event.setRespawnLocation(game.getCurrentMap().getSpectatorSpawn());
            }
        }
    }

    @EventHandler
    public void onPlayerOutOfBounds(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            if (!game.getCurrentMap().getBoundingBox().contains(event.getTo().toVector())) {
                player.setHealth(0); // This will trigger the PlayerDeathEvent
                player.sendMessage("You went out of bounds!");
            }
        }
    }
}