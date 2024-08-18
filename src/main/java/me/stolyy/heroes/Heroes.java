package me.stolyy.heroes;

import me.stolyy.heroes.Games.*;
import me.stolyy.heroes.heros.AbilityListener;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.heros.SetCharacterCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Field;
import java.util.UUID;

public final class Heroes extends JavaPlugin implements Listener {

    private static Heroes instance;
    private HeroManager heroManager;
    private PartyManager partyManager;
    private GameManager gameManager;
    private Location spawnLocation;

    public static Heroes getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Initialize managers
        heroManager = new HeroManager();
        partyManager = new PartyManager();
        gameManager = new GameManager(this, heroManager);

        // Set spawn location
        World world = Bukkit.getWorld("world");
        if (world != null) {
            spawnLocation = new Location(world, -106.5, 0, 184.5);
        } else {
            getLogger().warning("Default world not found. Spawn location not set.");
        }

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new AbilityListener(heroManager, gameManager), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(this, gameManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerHealthListener(this, gameManager), this);

        // Register commands
        registerCommands();

        // Start periodic tasks
        startPeriodicTasks();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Game game : gameManager.getActiveGames().values()) {
                for (Player player : game.getPlayers().keySet()) {
                    game.updatePlayerListHealth(player);
                }
            }
        }, 20L, 20L);

        getLogger().info("Heroes plugin has been enabled!");
    }

    private void registerCommands() {
        registerCommand("setcharacter", new SetCharacterCommand(heroManager));
        registerCommand("hero", new SetCharacterCommand(heroManager));
        registerCommand("party", new PartyCommand(partyManager));
        registerCommand("p", new PartyCommand(partyManager));
        registerCommand("partychat", new PartyChatCommand(partyManager));
        registerCommand("pc", new PartyChatCommand(partyManager));
        registerCommand("join", new JoinCommand(this, gameManager));
        registerCommand("spectate", new SpectateCommand(this, gameManager));
        registerCommand("leave", new LeaveCommand(this, gameManager));
        registerCommand("spectate", new SpectateCommand(this, gameManager));
    }

    private void registerCommand(String name, Command command) {
        try {
            Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(getServer());
            commandMap.register(name, command);

            for (String alias : command.getAliases()) {
                commandMap.register(alias, command);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Any join-specific logic can go here
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        partyManager.handlePlayerLeave(player);
        gameManager.leaveGame(player);
    }

    public void teleportToSpawn(Player player) {
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
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

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    @Override
    public void onDisable() {
        // Cleanup logic
        for (Player player : Bukkit.getOnlinePlayers()) {
            Game game = gameManager.getPlayerGame(player);
            if (game != null) {
                gameManager.leaveGame(player);
            }
        }

        // Cancel all tasks
        Bukkit.getScheduler().cancelTasks(this);

        getLogger().info("Heroes plugin has been disabled!");
    }
}