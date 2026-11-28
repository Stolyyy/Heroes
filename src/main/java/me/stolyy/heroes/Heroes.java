package me.stolyy.heroes;

import me.stolyy.heroes.ability.AbilityRegistry;
import me.stolyy.heroes.ability.kit.bug.CharmConfig;
import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.admin.BalanceCommand;
import me.stolyy.heroes.command.admin.ForceEndCommand;
import me.stolyy.heroes.command.admin.ForceHeroCommand;
import me.stolyy.heroes.command.admin.ForceReadyCommand;
import me.stolyy.heroes.command.admin.MapAdminCommand;
import me.stolyy.heroes.command.admin.MapPoolCommand;
import me.stolyy.heroes.command.admin.RestartCommand;
import me.stolyy.heroes.command.admin.TokenCommand;
import me.stolyy.heroes.command.admin.UnlockCommand;
import me.stolyy.heroes.command.general.AutoQueueCommand;
import me.stolyy.heroes.command.general.AutoSpectateCommand;
import me.stolyy.heroes.command.general.CharmsCommand;
import me.stolyy.heroes.command.general.JoinCommand;
import me.stolyy.heroes.command.general.LeaderboardCommand;
import me.stolyy.heroes.command.general.LobbyCommand;
import me.stolyy.heroes.command.general.PartyChatCommand;
import me.stolyy.heroes.command.general.PartyCommand;
import me.stolyy.heroes.command.general.PracticeCommand;
import me.stolyy.heroes.command.general.SetHeroCommand;
import me.stolyy.heroes.command.general.SettingsCommand;
import me.stolyy.heroes.command.general.ShardsCommand;
import me.stolyy.heroes.command.general.SkinCommand;
import me.stolyy.heroes.command.general.SkipMapCommand;
import me.stolyy.heroes.command.general.SpectateCommand;
import me.stolyy.heroes.command.general.StatsCommand;
import me.stolyy.heroes.command.general.StuckCommand;
import me.stolyy.heroes.command.general.VersionCommand;
import me.stolyy.heroes.discord.DiscordBot;
import me.stolyy.heroes.game.AutoSpectate;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.MapSkipPoll;
import me.stolyy.heroes.game.MatchLog;
import me.stolyy.heroes.game.PlayerVisibility;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.game.SpawnProtection;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.map.TemplateWorldContext;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.mechanics.LobbyMobility;
import me.stolyy.heroes.hero.minion.MinionManager;
import me.stolyy.heroes.listener.ChatFormatListener;
import me.stolyy.heroes.listener.HungerListener;
import me.stolyy.heroes.listener.PlayerJoinListener;
import me.stolyy.heroes.listener.PlayerLeaveListener;
import me.stolyy.heroes.npc.NpcConfig;
import me.stolyy.heroes.npc.NpcManager;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.util.BotConfig;
import me.stolyy.heroes.util.BotNameManager;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.BloodTrail;
import me.stolyy.heroes.vfx.effect.DisplayTags;
import me.stolyy.heroes.vfx.glow.TeamGlow;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.prompt.PromptManager;
import me.stolyy.heroes.vfx.ui.visual.BoardDisplay;
import me.stolyy.heroes.vfx.ui.visual.DiscordStatus;
import me.stolyy.heroes.vfx.ui.visual.EloBoardSource;
import me.stolyy.heroes.vfx.ui.visual.HeroNpcLevelTags;
import me.stolyy.heroes.vfx.ui.visual.HeroNpcSkins;
import me.stolyy.heroes.vfx.ui.visual.LevelBoardSource;
import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import me.stolyy.heroes.vfx.ui.visual.MemorialDisplays;
import me.stolyy.heroes.vfx.ui.visual.SkinMuseum;
import me.stolyy.heroes.vfx.ui.visual.TabList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.jar.JarFile;

public final class Heroes extends JavaPlugin {

    /** Milliseconds per Minecraft server tick (20 TPS). */
    public static final long MS_PER_TICK = 50L;
    /** Minecraft server ticks per second, derived from {@link #MS_PER_TICK}. */
    public static final double TICKS_PER_SECOND = 1000.0 / MS_PER_TICK;

    private static Heroes instance;
    private static Location lobbyLocation;
    private static MemorialDisplays memorialDisplays;
    private static SkinMuseum skinMuseum;

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

        NpcConfig.load();
        lobbyLocation = NpcConfig.getLobbySpawn();

        GameConfigManager.reload();
        GameModeConfigManager.reload();
        ProgressionConfig.load();
        GameMapManager.initialize();
        extractHeroResources();
        AbilityRegistry.registerAll();
        HeroConfig.load();
        // After HeroConfig.load(): async board seeding computes smashLevel(), which filters admin-only heroes.
        PlayerStatsManager.initialize();
        CharmConfig.load();
        HeroBuilder.validateAll();
        BotNameManager.load();
        BotConfig.load();
        Interactions.initialize();
        HeroManager.initialize();
        GameManager.initialize();
        MatchLog.initialize();
        GameManager.onRosterChange(TabList::refreshAll);
        QueueManager.initialize();
        PartyManager.initialize();
        GUIManager.initialize();
        PromptManager.initialize();

        registerListeners();
        registerCommands();

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            TemplateWorldContext.register();
        }

        TeamGlow.initialize();
        AutoSpectate.initialize();

        NpcManager.initialize();
        HeroNpcLevelTags.initialize();
        HeroNpcSkins.initialize();
        SkinMuseum.initialize();
        PracticeManager.initialize();
        DiscordStatus.start();
        DiscordBot.start();
        AbstractDisplayBuilder.sweepOrphans();

        new BoardDisplay<>(new EloBoardSource(), DisplayTags.LEADERBOARD);
        new BoardDisplay<>(new LevelBoardSource(), DisplayTags.LEVEL_LEADERBOARD);
        memorialDisplays = new MemorialDisplays();
        skinMuseum = new SkinMuseum();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            NpcManager.spawnLobbyNpcs();
            HeroNpcSkins.refreshAll();
            BoardDisplay.spawnAll();
            memorialDisplays.spawn();
            skinMuseum.spawn();
            PracticeManager.spawnDummies();
        }, 20L);

        getLogger().info("Heroes " + version() + " enabled");
    }

    @Override
    public void onDisable() {
        DiscordBot.shutdown();
        DiscordStatus.shutdown();
        GUIManager.clear();
        MapSkipPoll.clear();
        GameManager.clear();
        MatchLog.clear();
        HeroManager.clear();
        MinionManager.clear();
        PracticeManager.clear();
        QueueManager.shutdown();
        PartyManager.clear();
        AutoSpectate.clear();
        SpawnProtection.clear();
        BoardDisplay.despawnAll();
        if (memorialDisplays != null) memorialDisplays.despawn();
        if (skinMuseum != null) skinMuseum.despawn();
        HeroNpcLevelTags.clearAll();
        NpcManager.clear();
        TeamGlow.shutdown();
        PlayerStatsManager.shutdown();
        HeroConfig.clear();
        CosmeticManager.clearPreviews();
        BloodTrail.clear();
        AbstractDisplayBuilder.sweepOrphans();
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("Heroes plugin has been disabled!");
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerLeaveListener(), this);
        Bukkit.getPluginManager().registerEvents(new ChatFormatListener(), this);
        Bukkit.getPluginManager().registerEvents(new HungerListener(), this);
    }

    private void registerCommands() {
        registerCommand(new SetHeroCommand());
        registerCommand(new PartyCommand());
        registerCommand(new PartyChatCommand());
        registerCommand(new JoinCommand());
        registerCommand(new AutoQueueCommand());
        registerCommand(new SpectateCommand());
        registerCommand(new AutoSpectateCommand());
        registerCommand(new StatsCommand());
        registerCommand(new LobbyCommand());
        registerCommand(new StuckCommand());
        registerCommand(new CharmsCommand());
        registerCommand(new MapAdminCommand());
        registerCommand(new MapPoolCommand());
        registerCommand(new BalanceCommand());
        registerCommand(new ForceReadyCommand());
        registerCommand(new ForceEndCommand());
        registerCommand(new ForceHeroCommand());
        registerCommand(new UnlockCommand());
        registerCommand(new TokenCommand());
        registerCommand(new PracticeCommand());
        registerCommand(new SettingsCommand());
        registerCommand(new ShardsCommand());
        registerCommand(new SkinCommand());
        registerCommand(new VersionCommand());
        registerCommand(new RestartCommand());
        registerCommand(new SkipMapCommand());
        registerCommand(new LeaderboardCommand());
    }

    private void registerCommand(HeroesCommand command) {
        registerOpPermission(command.getPermission());
        command.extraPermissions().forEach(this::registerOpPermission);
        Bukkit.getCommandMap().register("heroes", command);
    }

    private void registerOpPermission(@Nullable String node) {
        if (node == null) return;
        if (Bukkit.getPluginManager().getPermission(node) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(node, PermissionDefault.OP));
        }
    }

    private void extractHeroResources() {
        saveResource("heroes/default.yml", false);
        try (JarFile jar = new JarFile(getFile())) {
            jar.stream()
                .filter(e -> e.getName().startsWith("heroes/") && e.getName().endsWith(".yml"))
                .forEach(e -> saveResource(e.getName(), false));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract hero resources from plugin jar", e);
        }
    }

    public static String version() { return instance.getPluginMeta().getVersion(); }

    public static void teleportToLobby(Player player) {
        if (lobbyLocation == null) {
            throw new IllegalStateException("Lobby location is not initialized");
        }
        PracticeManager.exitPractice(player, false);
        player.teleport(lobbyLocation);
        PlayerVisibility.refreshVisibility(player);
        player.getInventory().clear();

        LobbyMobility.apply(player);
        player.setFoodLevel(20);
        player.setExp(0);
        player.setLevel(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            if (!player.isOnline() || GameManager.isPlayerInGame(player) || PracticeManager.isPracticing(player)) return;
            player.setGameMode(GameMode.ADVENTURE);
            // setGameMode(ADVENTURE) clears allowFlight in Bukkit, so re-arm lobby mobility after applying it.
            LobbyMobility.apply(player);
        }, 1L);
        LobbyScoreboard.apply(player);
        LobbyScoreboard.refreshAll();
        player.sendMessage("You have been teleported to the lobby.");
    }
}