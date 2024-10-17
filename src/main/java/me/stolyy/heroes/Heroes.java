package me.stolyy.heroes;

import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.oldGames.*;
import me.stolyy.heroes.oldGames.Party.PartyChatCommand;
import me.stolyy.heroes.oldGames.Party.PartyCommand;
import me.stolyy.heroes.oldGames.Party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Level;

public final class Heroes extends JavaPlugin implements Listener {

    private static Heroes instance;
    private HeroManager heroManager;
    private PartyManager partyManager;
    private GameManager gameManager;
    private Location lobbyLocation;

    public static Heroes getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Initialize managers
        heroManager = new HeroManager();
        partyManager = new PartyManager();
        gameManager = new GameManager(heroManager, partyManager);

        // Set lobby location
        World world = Bukkit.getWorld("world");
        if (world != null) {
            this.lobbyLocation = new Location(world, 0.5, 0, 500.5);
        } else {
            getLogger().warning("Default world not found. Lobby location not set.");
        }

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new AbilityListener(heroManager, gameManager), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(gameManager), this);

        registerCommands();
        startPeriodicTasks();

        getLogger().info("Heroes plugin has been enabled!");
    }

    private void registerCommands() {
        registerCommand("setcharacter", new SetCharacterCommand(heroManager));
        registerCommand("hero", new SetCharacterCommand(heroManager));
        registerCommand("party", new PartyCommand(partyManager));
        registerCommand("p", new PartyCommand(partyManager));
        registerCommand("partychat", new PartyChatCommand(partyManager));
        registerCommand("pc", new PartyChatCommand(partyManager));
        registerCommand("join", new JoinCommand(gameManager));
        registerCommand("spectate", new SpectateCommand(this, gameManager));
        registerCommand("leave", new LeaveCommand(this, gameManager));
    }

    private void registerCommand(String name, Command command) {
        try {
            final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
            commandMap.register(name, command);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Failed to register command: " + name, e);
        }
    }

    private void startPeriodicTasks() {
        // Task to clean up inactive games
        new BukkitRunnable() {
            @Override
            public void run() {
                gameManager.cleanupGames();
            }
        }.runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5); // Run every 5 minutes

        // Task to update player health and check positions
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Game game : gameManager.getActiveGames().values()) {
                    if (game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
                        for (UUID playerUUID : game.getPlayers().keySet()) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player != null) {
                                //game.updateScoreboard();
                                game.checkPlayerPosition(player);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Run every second
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        teleportToLobby(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        partyManager.leaveParty(player);
        gameManager.leaveGame(player);
        heroManager.removePlayer(player);
    }

    public void teleportToLobby(Player player) {
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setExp(0);
            player.setLevel(0);

            // Remove all potion effects
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }

            player.sendMessage("You have been teleported to the lobby.");
        } else {
            getLogger().warning("Attempted to teleport player to lobby, but lobby location is not set.");
        }
    }

    public HeroManager getHeroManager() {
        return heroManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    @Override
    public void onDisable() {
        // Cancel all tasks
        Bukkit.getScheduler().cancelTasks(this);

        // End all active games
        for (Game game : gameManager.getActiveGames().values()) {
            game.endGame(null);
        }

        // Clear data structures
        heroManager.clear();
        partyManager.clear();
        gameManager.clearGames();

        getLogger().info("Heroes plugin has been disabled!");
    }
}