package me.stolyy.heroes;

import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.game.menus.GUIListener;
import me.stolyy.heroes.game.minigame.*;
import me.stolyy.heroes.heros.listeners.AbilityListener;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.heros.SetHeroCommand;
import me.stolyy.heroes.heros.listeners.DoubleJumpListener;
import me.stolyy.heroes.heros.listeners.JabListener;
import me.stolyy.heroes.party.PartyChatCommand;
import me.stolyy.heroes.party.PartyCommand;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.utility.commands.LobbyCommand;
import me.stolyy.heroes.utility.commands.SpectateCommand;
import me.stolyy.heroes.utility.commands.StuckCommand;
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
import java.util.logging.Level;

public final class Heroes extends JavaPlugin implements Listener {

    private static Heroes instance;
    private Location lobbyLocation;

    public static Heroes getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        World world = Bukkit.getWorld("world");
        if (world != null) {
            this.lobbyLocation = new Location(world, 0.5, 0, 500.5);
        } else {
            getLogger().warning("Default world not found. Lobby location not set.");
        }

        // Register event listeners
        GameMapManager.initializeMaps();

        registerListeners();
        registerCommands();

        startPeriodicTasks();

        getLogger().info("Heroes plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);

        // End all active games
        for (Game game : GameManager.getActiveGames()) {
            game.gameEnd();
        }

        // Clear data structures
        HeroManager.clear();
        //GameManager.clear();
        //PartyManager.clear();

        getLogger().info("Heroes plugin has been disabled!");
    }


    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new AbilityListener(), this);
        Bukkit.getPluginManager().registerEvents(new DoubleJumpListener(), this);
        Bukkit.getPluginManager().registerEvents(new JabListener(), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(), this);
    }

    private void registerCommands() {
        registerCommand("setcharacter", new SetHeroCommand());
        registerCommand("hero", new SetHeroCommand());
        registerCommand("sethero", new SetHeroCommand());
        registerCommand("party", new PartyCommand());
        registerCommand("p", new PartyCommand());
        registerCommand("partychat", new PartyChatCommand());
        registerCommand("pc", new PartyChatCommand());
        registerCommand("join", new JoinCommand());
        registerCommand("spectate", new SpectateCommand());
        registerCommand("leave", new LobbyCommand());
        registerCommand("lobby", new LobbyCommand());
        registerCommand("hub", new LobbyCommand());
        registerCommand("l", new LobbyCommand());
        registerCommand("gg", new LobbyCommand());
        registerCommand("stuck", new StuckCommand());
        registerCommand("s", new StuckCommand());
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
        // Task to update player health and check positions
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Game game : GameManager.getActiveGames()) {
                    if (game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
                        for (Player player : game.getPlayerList()) {
                            if (player != null) {
                                game.updateVisuals();
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
        PartyManager.leaveParty(player);
        GameManager.leaveGame(player);
        HeroManager.removePlayer(player);
    }

    public void teleportToLobby(Player player) {
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
            player.getInventory().clear();
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setExp(0);
            player.setLevel(0);

            // Remove all potion effects
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            Bukkit.getScheduler().runTaskLater(this, () -> player.setGameMode(GameMode.ADVENTURE), 1L);
            player.sendMessage("You have been teleported to the lobby.");
        } else {
            getLogger().warning("Attempted to teleport player to lobby, but lobby location is not set.");
        }
    }
}