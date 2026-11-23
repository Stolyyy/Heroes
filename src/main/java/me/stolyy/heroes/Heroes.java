package me.stolyy.heroes;

import me.stolyy.heroes.command.admin.BalanceCommand;
import me.stolyy.heroes.command.admin.RestartCommand;
import me.stolyy.heroes.command.general.SkipMapCommand;
import me.stolyy.heroes.listener.ChatFormatListener;
import me.stolyy.heroes.vfx.ui.gui.CustomGameHotbarListener;
import me.stolyy.heroes.command.general.VersionCommand;
import me.stolyy.heroes.command.admin.ForceEndCommand;
import me.stolyy.heroes.command.admin.ForceReadyCommand;
import me.stolyy.heroes.command.admin.MapAdminCommand;
import me.stolyy.heroes.command.admin.MapPoolCommand;
import me.stolyy.heroes.command.admin.TokenCommand;
import me.stolyy.heroes.command.admin.UnlockCommand;
import me.stolyy.heroes.command.general.*;
import me.stolyy.heroes.game.AutoSpectate;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.listener.*;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.prompt.PromptListener;
import me.stolyy.heroes.util.BotNameManager;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.ability.AbilityRegistry;
import me.stolyy.heroes.ability.kit.bug.CharmConfig;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.vfx.ui.visual.LeaderboardDisplay;
import me.stolyy.heroes.vfx.ui.visual.LevelLeaderboardDisplay;
import me.stolyy.heroes.vfx.ui.visual.MemorialDisplays;
import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import me.stolyy.heroes.npc.NpcManager;
import me.stolyy.heroes.util.TemplateWorldContext;
import me.stolyy.heroes.vfx.glow.TeamGlow;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class Heroes extends JavaPlugin {

    private static Heroes instance;
    private static Location lobbyLocation;
    private static LeaderboardDisplay leaderboardDisplay;
    private static LevelLeaderboardDisplay levelLeaderboardDisplay;
    private static MemorialDisplays memorialDisplays;

    public static Heroes getInstance() {
        return instance;
    }

    public static BukkitScheduler getScheduler() {
        return getInstance().getServer().getScheduler();
    }

    public static Location getLobbyLocation() {
        return lobbyLocation;
    }

    @Override
    public void onEnable() {
        instance = this;

        World world = Bukkit.getWorld("world");
        if (world != null) {
            lobbyLocation = new Location(world, 0.5, 0, 500.5);
        } else {
            getLogger().warning("Default world not found. Lobby location not set.");
        }

        GameConfigManager.reload();
        GameModeConfigManager.reload();
        ProgressionConfig.load();
        GameMapManager.initialize();
        PlayerStatsManager.initialize();
        extractHeroResources();
        AbilityRegistry.registerAll();
        HeroConfig.load();
        CharmConfig.load();
        HeroBuilder.validateAll();
        BotNameManager.load();
        me.stolyy.heroes.util.BotConfig.load();
        QueueManager.initialize();
        PartyManager.initialize();

        registerListeners();
        registerCommands();
        registerPermissions();

        TemplateWorldContext.register();
        AbilityListener.startHeldFireTask();

        TeamGlow.initialize();
        AutoSpectate.initialize();

        NpcManager.initialize();
        PracticeManager.initialize();

        leaderboardDisplay = new LeaderboardDisplay();
        levelLeaderboardDisplay = new LevelLeaderboardDisplay();
        memorialDisplays = new MemorialDisplays();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            NpcManager.spawnLobbyNpcs();
            leaderboardDisplay.spawn();
            levelLeaderboardDisplay.spawn();
            memorialDisplays.spawn();
            PracticeManager.spawnDummies();
        }, 20L);

        getLogger().info("Heroes " + version() + " enabled");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        GUIManager.clear();
        if (leaderboardDisplay != null) leaderboardDisplay.despawn();
        if (levelLeaderboardDisplay != null) levelLeaderboardDisplay.despawn();
        if (memorialDisplays != null) memorialDisplays.despawn();
        me.stolyy.heroes.vfx.ui.visual.HeroNpcLevelTags.clearAll();
        NpcManager.clear();
        TeamGlow.shutdown();
        PracticeManager.clear();
        QueueManager.shutdown();
        PlayerStatsManager.shutdown();
        GameManager.clear();
        HeroManager.clear();
        PartyManager.clear();
        getLogger().info("Heroes plugin has been disabled!");
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerLeaveListener(), this);
        Bukkit.getPluginManager().registerEvents(new ActivityListener(), this);
        Bukkit.getPluginManager().registerEvents(new AbilityListener(), this);
        Bukkit.getPluginManager().registerEvents(new MovementListener(), this);
        Bukkit.getPluginManager().registerEvents(new JabListener(), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(), this);
        Bukkit.getPluginManager().registerEvents(new SpectatorListener(), this);
        Bukkit.getPluginManager().registerEvents(new HungerListener(), this);
        Bukkit.getPluginManager().registerEvents(new LeaderboardListener(), this);
        Bukkit.getPluginManager().registerEvents(new PromptListener(), this);
        Bukkit.getPluginManager().registerEvents(new GreenHoodListener(), this);
        Bukkit.getPluginManager().registerEvents(new RespawnRepickListener(), this);
        Bukkit.getPluginManager().registerEvents(new KnockbackListener(), this);
        Bukkit.getPluginManager().registerEvents(new PracticeManager(), this);
        Bukkit.getPluginManager().registerEvents(new ChatFormatListener(), this);
        Bukkit.getPluginManager().registerEvents(new CustomGameHotbarListener(), this);
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
        registerCommand("queue", new JoinCommand());
        registerCommand("q", new JoinCommand());
        registerCommand("play", new JoinCommand());
        registerCommand("autoqueue", new AutoQueueCommand());
        registerCommand("aq", new AutoQueueCommand());
        registerCommand("spectate", new SpectateCommand());
        registerCommand("spec", new SpectateCommand());
        registerCommand("autospectate", new AutoSpectateCommand());
        registerCommand("autospec", new AutoSpectateCommand());
        registerCommand("aspec", new AutoSpectateCommand());
        registerCommand("as", new AutoSpectateCommand());
        registerCommand("stats", new StatsCommand());
        registerCommand("leave", new LobbyCommand());
        registerCommand("lobby", new LobbyCommand());
        registerCommand("hub", new LobbyCommand());
        registerCommand("l", new LobbyCommand());
        registerCommand("gg", new LobbyCommand());
        registerCommand("stuck", new StuckCommand());
        registerCommand("s", new StuckCommand());
        registerCommand("charms", new CharmsCommand());
        registerCommand("charm", new CharmsCommand());
        registerCommand("bugcharms", new CharmsCommand());
        registerCommand("bugcharm", new CharmsCommand());
        registerCommand("ma", new MapAdminCommand());
        registerCommand("mapadmin", new MapAdminCommand());
        registerCommand("mappool", new MapPoolCommand());
        registerCommand("mp", new MapPoolCommand());
        registerCommand("map_pool", new MapPoolCommand());
        registerCommand("maps", new MapAdminCommand());
        registerCommand("editmap", new MapAdminCommand());
        registerCommand("editmaps", new MapAdminCommand());
        registerCommand("managemaps", new MapAdminCommand());
        registerCommand("balance", new BalanceCommand());
        registerCommand("bal", new BalanceCommand());
        registerCommand("herobalance", new BalanceCommand());
        registerCommand("forceready", new ForceReadyCommand());
        registerCommand("fr", new ForceReadyCommand());
        registerCommand("ready", new ForceReadyCommand());
        registerCommand("forceend", new ForceEndCommand());
        registerCommand("fe", new ForceEndCommand());
        registerCommand("endgame", new ForceEndCommand());
        registerCommand("unlock", new UnlockCommand());
        registerCommand("unlockhero", new UnlockCommand());
        registerCommand("token", new TokenCommand());
        registerCommand("tokens", new TokenCommand());
        registerCommand("practice", new PracticeCommand());
        registerCommand("version", new VersionCommand());
        registerCommand("restart", new RestartCommand());
        registerCommand("skipmap", new SkipMapCommand());
        registerCommand("leaderboard", new LeaderboardCommand());
        registerCommand("lb", new LeaderboardCommand());
        registerCommand("elo", new LeaderboardCommand());
    }

    private void registerPermissions() {
        String[] opNodes = {
                "heroes.balance", "heroes.token", "heroes.unlock", "heroes.forceready",
                "heroes.forceend", "heroes.mapadmin", "heroes.mappool",
                "heroes.mapadmin.map.*", "heroes.mapadmin.create", "heroes.restart"
        };
        for (String node : opNodes) {
            if (Bukkit.getPluginManager().getPermission(node) == null) {
                Bukkit.getPluginManager().addPermission(new org.bukkit.permissions.Permission(
                        node, org.bukkit.permissions.PermissionDefault.OP));
            }
        }
    }

    private void registerCommand(String name, Command command) {
        try {
            final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
            commandMap.register(name, "heroes", command);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Failed to register command: " + name, e);
        }
    }

    private void extractHeroResources() {
        saveResource("heroes/default.yml", false);
        try (JarFile jar = new JarFile(getFile())) {
            jar.stream()
                .filter(e -> e.getName().startsWith("heroes/") && e.getName().endsWith(".yml"))
                .forEach(e -> saveResource(e.getName(), false));
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to scan hero resources", e);
        }
    }

    public static String version() { return instance.getPluginMeta().getVersion(); }

    public static void teleportToLobby(Player player) {
        if (lobbyLocation != null) {
            PracticeManager.exitPractice(player, false);
            player.teleport(lobbyLocation);
            player.getInventory().clear();

            player.setFlying(false);
            player.setAllowFlight(false);
            player.setFoodLevel(20);
            player.setExp(0);
            player.setLevel(0);

            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }

            Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
                if (GameManager.isPlayerInGame(player)) return;
                player.setGameMode(GameMode.ADVENTURE);
            }, 1L);
            LobbyScoreboard.apply(player);
            LobbyScoreboard.refreshAll();
            player.sendMessage("You have been teleported to the lobby.");
        } else {
            getInstance().getLogger().warning("Attempted to teleport player to lobby, but lobby location is not set.");
        }
    }

    public static LeaderboardDisplay getLeaderboardDisplay() {
        return leaderboardDisplay;
    }

    public static LevelLeaderboardDisplay getLevelLeaderboardDisplay() {
        return levelLeaderboardDisplay;
    }
}