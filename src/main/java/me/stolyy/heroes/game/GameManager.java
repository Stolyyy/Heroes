package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.queue.AutoQueue;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.game.rules.RespawnRepickListener;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.physics.WallDetection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Global registry of live/started matches and participant-to-game entity mappings. */
public class GameManager {
    // Started matches (STARTING / IN_PROGRESS); excludes WAITING lobbies.
    private static final Set<Game> activeGames = new LinkedHashSet<>();
    // Every constructed match whose world has not yet been deleted (includes WAITING lobbies).
    private static final Set<Game> liveGames = new LinkedHashSet<>();
    // Concurrent: async chat rendering resolves sender games off the main thread.
    private static final Map<UUID, Game> entityGames = new ConcurrentHashMap<>();
    private static Runnable rosterChangeHook = () -> {};

    public static void onRosterChange(Runnable hook) {
        rosterChangeHook = hook;
    }

    static void registerLive(Game game) {
        liveGames.add(game);
    }

    static void unregisterLive(Game game) {
        liveGames.remove(game);
    }

    public static void joinGame(Player player, GameMode gameMode) {
        joinGame(player, gameMode, null);
    }

    /** Joins a queue or custom match; {@code mapName} is honored only for {@link GameMode#CUSTOM}. */
    public static void joinGame(Player player, GameMode gameMode, @Nullable String mapName) {
        joinWithPreset(player, gameMode, null, mapName);
    }

    /** Joins a queue or custom match, optionally applying a {@link GameModePreset} and opening its settings GUI. */
    public static void joinWithPreset(Player player, GameMode gameMode,
                                      @Nullable GameModePreset preset, @Nullable String mapName) {
        if (!QueueManager.admit(player)) return;

        Party callerParty = PartyManager.getPlayerParty(player);
        if (callerParty != null && !callerParty.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the party leader can join games.", NamedTextColor.RED));
            return;
        }

        if (entityGames.containsKey(player.getUniqueId())) {
            // Spectators joining a queue keep watching until their match pops; custom joins always detach.
            if (gameMode == GameMode.CUSTOM || !isSpectating(player)) {
                detach(player);
            }
        }

        if (gameMode == GameMode.CUSTOM) {
            CustomLobby.open(player, mapName, preset);
        } else {
            if (mapName != null) {
                player.sendMessage(Component.text("Map selection is only available for custom games.", NamedTextColor.YELLOW));
            }
            QueueManager.joinQueue(player, gameMode);
        }
    }

    public static void spectateGame(Player spectator, Player target) {
        Game game = getPlayerGame(target);
        if (game == null || (game.gameState() != GameState.IN_PROGRESS && game.gameState() != GameState.STARTING)) {
            spectator.sendMessage(Component.text("That player is not in an active game.", NamedTextColor.RED));
            return;
        }

        detach(spectator);
        game.addPlayer(spectator, TeamColor.SPECTATOR);
        spectator.sendMessage(Component.text("You are now spectating " + target.getName() + "'s game.", NamedTextColor.AQUA));
        rosterChangeHook.run();
    }

    /** Detaches {@code player} from their current match without teleporting them to the lobby. */
    public static void detach(Player player) {
        leave(player, false);
    }

    /** Leaves any current match (or disarms auto-queue from lobby) and returns {@code player} to lobby. */
    public static void leaveToLobby(Player player) {
        leave(player, true);
    }

    private static void leave(Player player, boolean teleportToLobby) {
        Game game = getPlayerGame(player);
        if (game == null) {
            AutoQueue.stop(player);
            if (teleportToLobby) Heroes.teleportToLobby(player);
            return;
        }

        if (game.gameState() == GameState.WAITING && game.gameMode() != GameMode.ONE_V_ONE && PartyManager.isInParty(player)) {
            PartyManager.getPartyMembers(player).forEach(member -> {
                game.removePlayer(member);
                if (teleportToLobby) Heroes.teleportToLobby(member);
            });
        } else {
            game.removePlayer(player);
            if (teleportToLobby) Heroes.teleportToLobby(player);
        }

        boolean empty = game.onlinePlayers().isEmpty()
                || (game.gameState() == GameState.WAITING && game.onlinePlayers(false).isEmpty());
        if (empty) {
            game.end(GameEndReason.FORFEIT);
        }
    }

    public static Game changeMap(Game oldGame, GameMap newMap) {
        Game newGame = oldGame.createSuccessor(newMap);

        Map<Player, TeamColor> playerTeams = new HashMap<>();
        for (Player p : oldGame.onlinePlayers()) {
            playerTeams.put(p, oldGame.playerColor(p.getUniqueId()));
        }

        for (Map.Entry<Player, TeamColor> entry : playerTeams.entrySet()) {
            Player p = entry.getKey();
            oldGame.transferPlayerTo(newGame, p, entry.getValue(),
                    oldGame.getGamePlayer(p.getUniqueId()).heroOverride());
        }

        oldGame.transferBotsTo(newGame);
        oldGame.cleanupWorld();
        return newGame;
    }

    /** Starts a matched queue game from already-dequeued teams. */
    public static void startQueuedGame(List<Player> team1, List<Player> team2, GameMode mode) {
        GameMap map = GameMapManager.getRandomMap(mode);
        Game game = Game.create(map, mode);

        for (Player p : team1) {
            if (isPlayerInGame(p)) detach(p);
            game.addPlayer(p, TeamColor.RED);
        }
        for (Player p : team2) {
            if (isPlayerInGame(p)) detach(p);
            game.addPlayer(p, TeamColor.BLUE);
        }

        game.start();
    }

    // === Target Validation & Entity Lookup ===

    public static Game getEntityGame(UUID uuid) {
        Game direct = entityGames.get(uuid);
        if (direct != null) return direct;

        Hero hero = HeroManager.getHero(uuid);
        if (hero instanceof MinionHero minion) return minion.getGame();
        return null;
    }

    public static Game getEntityGame(LivingEntity entity) {
        return getEntityGame(entity.getUniqueId());
    }

    public static List<LivingEntity> getEnemies(LivingEntity entity) {
        Game game = getEntityGame(entity.getUniqueId());
        if (game != null) return game.getEnemies(entity.getUniqueId());
        return PracticeManager.enemiesFor(entity);
    }

    public static boolean canTarget(LivingEntity attacker, LivingEntity victim, Boolean overrideFriendlyFire) {
        if (attacker == null || victim == null) return false;
        if (PracticeManager.isPracticeHit(attacker, victim)) return true;
        Game attackerGame = getEntityGame(attacker);
        Game victimGame = getEntityGame(victim);
        if (attackerGame == null || !attackerGame.equals(victimGame)) return false;
        return attackerGame.canTarget(attacker, victim, overrideFriendlyFire);
    }

    public static boolean canTarget(LivingEntity attacker, LivingEntity victim) {
        return canTarget(attacker, victim, null);
    }

    public static void registerEntity(UUID uuid, Game game) {
        entityGames.put(uuid, game);
    }

    public static void unregisterEntity(UUID uuid) {
        entityGames.remove(uuid);
    }

    public static Game getPlayerGame(Player player) {
        return player == null ? null : entityGames.get(player.getUniqueId());
    }

    public static boolean isRespawning(Player player) {
        Game game = getPlayerGame(player);
        return game != null && game.isRespawning(player.getUniqueId());
    }

    public static boolean isPlayerInGame(Player player) {
        return player != null && entityGames.containsKey(player.getUniqueId());
    }

    public static boolean isSpectating(Player player) {
        Game game = getPlayerGame(player);
        return game != null && game.playerColor(player.getUniqueId()) == TeamColor.SPECTATOR;
    }

    /** True when {@code player} may activate hero abilities (active match combatant or practicing in lobby). */
    public static boolean canUseAbility(Player player) {
        Game game = getPlayerGame(player);
        if (game == null) {
            return PracticeManager.isPracticing(player)
                    && player.getGameMode() == org.bukkit.GameMode.ADVENTURE
                    && HeroManager.getHero(player) != null;
        }
        return game.gameState() == GameState.IN_PROGRESS
                && player.getGameMode() == org.bukkit.GameMode.ADVENTURE
                && !game.isRespawning(player.getUniqueId())
                && game.onlinePlayers(false).contains(player);
    }

    /** Teleports a match player trapped in blocks back to a spawn point; false outside a match or if not trapped. */
    public static boolean unstuck(Player player) {
        Game game = getPlayerGame(player);
        if (game == null || !WallDetection.isTrapped(player)) return false;
        player.teleport(game.getRespawnLocation(player, null, player.getLocation().clone()));
        return true;
    }

    public static void initialize() {
        Heroes plugin = Heroes.getInstance();
        Bukkit.getPluginManager().registerEvents(new GameListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new SpectatorListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new RespawnRepickListener(), plugin);
    }

    public static void clear() {
        Set<Game> all = new HashSet<>(liveGames);
        all.addAll(entityGames.values());
        all.forEach(Game::clean);
        entityGames.clear();
        activeGames.clear();
        liveGames.clear();
        GameListener.clear();
    }

    public static Set<Game> getActiveGames() {
        return Collections.unmodifiableSet(activeGames);
    }

    public static void addToActive(Game game) {
        activeGames.add(game);
        Bukkit.getScheduler().runTask(Heroes.getInstance(), rosterChangeHook);
    }

    public static void removeFromActive(Game game) {
        activeGames.remove(game);
        rosterChangeHook.run();
    }
}
