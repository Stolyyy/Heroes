package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.npc.types.GameBotNpc;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.game.queue.AutoQueue;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.ui.visual.TabList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.List;

public class GameManager {
    private static final Set<Game> activeGames = new LinkedHashSet<>();
    // Concurrent: the async chat renderer resolves a sender's game off the main thread.
    private static final Map<UUID, Game> entityGames = new java.util.concurrent.ConcurrentHashMap<>();


    public static void joinGame(Player player, GameMode gameMode) {
        joinGame(player, gameMode, null);
    }

    /**
     * Join a game, optionally specifying a map by name. {@code mapName} is only honored for
     * {@link GameMode#CUSTOM} — for other modes a non-null name is rejected with a notice.
     */
    public static void joinGame(Player player, GameMode gameMode, String mapName) {
        if (RestartManager.isDraining()) {
            player.sendMessage(GameConfigManager.getMessage("restart.queue-closed"));
            return;
        }

        // Party gate: if you are in a party, only the leader can initiate a join.
        // Non-leader members come along with the leader (custom + 2v2); they cannot join on their own.
        Party callerParty = PartyManager.getPlayerParty(player);
        if (callerParty != null && !callerParty.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the party leader can join games.", NamedTextColor.RED));
            return;
        }

        if (entityGames.containsKey(player.getUniqueId())) {
            // Spectators joining a QUEUE keep watching their game until the match pops;
            // custom joins (and active players) still leave first.
            if (gameMode == GameMode.CUSTOM || !isSpectating(player)) {
                leaveGame(player, false);
            }
        }

        if (gameMode == GameMode.CUSTOM) {
            handleCustomJoin(player, mapName);
        } else {
            if (mapName != null) {
                player.sendMessage(Component.text(
                        "Map selection is only available for custom games.", NamedTextColor.YELLOW));
            }
            QueueManager.joinQueue(player, gameMode);
        }
    }

    public static void spectateGame(Player spectator, Player target) {
        Game game = getPlayerGame(target);
        // STARTING counts: joining during the countdown means watching from the first second,
        // and it's what lets /autospec fire the moment the target's game begins. WAITING custom
        // lobbies stay excluded — there is nothing to watch yet.
        if (game == null || (game.gameState() != GameState.IN_PROGRESS
                && game.gameState() != GameState.STARTING)) {
            spectator.sendMessage(Component.text("That player is not in an active game.", NamedTextColor.RED));
            return;
        }

        leaveGame(spectator, false);

        game.addPlayer(spectator, TeamColor.SPECTATOR);
        entityGames.put(spectator.getUniqueId(), game);
        spectator.sendMessage(Component.text("You are now spectating " + target.getName() + "'s game.", NamedTextColor.AQUA));
        TabList.refreshAll();
    }

    public static void leaveGame(Player player, boolean teleportToLobby) {
        Game game = getPlayerGame(player);
        if (game == null) {
            // A lobby player asking to go to the lobby is /lobby's fall-through: nothing to leave
            // but the auto-queue countdown, so treat it as the cancel. Every other caller reaches
            // this method with a game attached, so a mid-loop requeue is not disarmed here.
            AutoQueue.stop(player);
            if (teleportToLobby) Heroes.teleportToLobby(player);
            return;
        }

        // If a player leaves a waiting 2v2/party game, the whole party leaves.
        if (game.gameState() == GameState.WAITING && game.gameMode() != GameMode.ONE_V_ONE && PartyManager.isInParty(player)) {
            Set<Player> partyMembers = PartyManager.getPartyMembers(player);
            partyMembers.forEach(member -> {
                game.removePlayer(member);
                entityGames.remove(member.getUniqueId());
                if (teleportToLobby) Heroes.teleportToLobby(member);
            });
        } else {
            game.removePlayer(player);
            entityGames.remove(player.getUniqueId());
            if (teleportToLobby) Heroes.teleportToLobby(player);
        }

        if (game.onlinePlayers().isEmpty()) {
            game.end(GameEndReason.FORFEIT);
            activeGames.remove(game);
        }
    }

    public static Game changeMap(Game oldGame, GameMap newMap) {
        Game newGame = new Game(newMap, oldGame.gameMode());

        newGame.settings().copy(oldGame.settings());
        newGame.defaultTeamSettings().copy(oldGame.defaultTeamSettings());
        // Carry the gamemode: preset for display, then the (possibly GUI-tweaked) config verbatim.
        newGame.setGameModePreset(oldGame.gameModePreset());
        newGame.setModeConfig(oldGame.modeConfig());
        newGame.setBossPlayer(oldGame.bossPlayer());

        Map<Player, TeamColor> playerTeams = new HashMap<>();
        for (Player p : oldGame.onlinePlayers()) {
            playerTeams.put(p, oldGame.playerColor(p.getUniqueId()));
        }

        // Silently remove players from old game (no teleport, no end check)
        for (Player player : playerTeams.keySet()) {
            oldGame.silentRemove(player);
        }

        // Add players to new game, carrying over each player's own settings copy + hero override
        for (Map.Entry<Player, TeamColor> entry : playerTeams.entrySet()) {
            Player player = entry.getKey();
            TeamColor teamColor = entry.getValue();

            newGame.addPlayer(player, teamColor);
            entityGames.put(player.getUniqueId(), newGame);

            GamePlayer oldState = oldGame.getGamePlayer(player.getUniqueId());
            GamePlayer newState = newGame.getGamePlayer(player.getUniqueId());
            newState.settings().copy(oldState.settings());
            newState.setHeroOverride(oldState.heroOverride());
        }

        // Bots are respawned in the new world rather than teleported: Citizens defers a cross-world
        // NPC teleport by 5 ticks, but the old world is unloaded on this tick and its unload
        // listener despawns every NPC still standing there.
        for (Map.Entry<UUID, GameBot> entry : new ArrayList<>(oldGame.getGameBots().entrySet())) {
            UUID oldUUID = entry.getKey();
            GameBot bot = entry.getValue();
            String name = bot.getName();
            String heroName = bot.getHeroName();
            int tier = bot.getDifficultyTier();
            TeamColor teamColor = oldGame.playerColor(oldUUID);
            PlayerSettings oldSettings = oldGame.getGamePlayer(oldUUID).settings();
            oldGame.removeBot(oldUUID);
            NpcServiceProvider.get().remove(bot.getNpcHandle().id());

            GameBotNpc npc = new GameBotNpc(name, newGame.settings().map().spawnLocations().get(0));
            npc.spawn();
            if (npc.getHandle() == null) {
                Heroes.getInstance().getLogger().severe("Could not respawn bot " + name
                        + " in the new map; dropping it");
                continue;
            }

            GameBot fresh = new GameBot(name, heroName, npc.getHandle());
            fresh.setDifficultyTier(tier);
            if (!newGame.addBot(fresh, teamColor)) {
                Heroes.getInstance().getLogger().severe("Could not carry bot " + name
                        + " into the new map; removing its NPC.");
                NpcServiceProvider.get().remove(npc.getHandle().id());
                continue;
            }

            UUID newUUID = NpcServiceProvider.get().getBukkitEntity(fresh.getNpcHandle()).getUniqueId();
            newGame.getGamePlayer(newUUID).settings().copy(oldSettings);
        }

        // Cleanup old world without affecting players
        oldGame.cleanupWorld();
        return newGame;
    }

    public static void startQueuedGame(List<Player> team1, List<Player> team2, GameMode mode) {
        GameMap map = GameMapManager.getRandomMap(mode);
        Game game = new Game(map, mode);

        for (Player p : team1) {
            QueueManager.removeFromAllQueues(p);
            if (isPlayerInGame(p)) leaveGame(p, false); // detach queued spectators cleanly
            game.addPlayer(p, TeamColor.RED);
            entityGames.put(p.getUniqueId(), game);
        }
        for (Player p : team2) {
            QueueManager.removeFromAllQueues(p);
            if (isPlayerInGame(p)) leaveGame(p, false);
            game.addPlayer(p, TeamColor.BLUE);
            entityGames.put(p.getUniqueId(), game);
        }

        game.start();
    }

    private static void handleCustomJoin(Player player, String mapName) {
        // No party required. It was only ever a soft gate — you could party yourself and join
        // solo — and now that customs can be filled with bots, a one-player lobby is a legitimate
        // way to play. A partyless player simply brings a roster of one.
        Party party = PartyManager.getPlayerParty(player);
        if (party != null && party.getSize() > GameMode.CUSTOM.maxTotalPlayers()) {
            player.sendMessage(Component.text("Your party is too large for this mode (max " + GameMode.CUSTOM.maxTotalPlayers() + " players).", NamedTextColor.RED));
            return;
        }

        // Reject if any party member is already in a game
        for (Player member : PartyManager.getPartyMembers(player)) {
            if (entityGames.containsKey(member.getUniqueId())) {
                player.sendMessage(Component.text(
                        member.getName() + " is currently in a game.",
                        NamedTextColor.RED));
                return;
            }
        }

        // Resolve the map: caller-specified name if given, else random across modes.
        GameMap map;
        if (mapName != null) {
            map = GameMapManager.findMapByName(mapName);
            if (map == null) {
                player.sendMessage(Component.text(
                        "Map not found: " + mapName, NamedTextColor.RED));
                return;
            }
        } else {
            map = GameMapManager.getRandomMapInAll(Set.of(GameMode.ONE_V_ONE, GameMode.TWO_V_TWO));
        }

        Game game = new Game(map, GameMode.CUSTOM);

        List<Player> members = new ArrayList<>(PartyManager.getPartyMembers(player));
        if (members.isEmpty()) members.add(player); // partyless host: a roster of one
        members.forEach(member -> {
            QueueManager.removeFromAllQueues(member);
            entityGames.put(member.getUniqueId(), game);
        });

        members.remove(player);
        game.addPlayer(player, TeamColor.RED);
        if (!members.isEmpty()) game.addPlayer(members.getFirst(), TeamColor.BLUE);

        for (int i = 1; i < members.size(); i++) {
            game.addPlayer(members.get(i), TeamColor.SPECTATOR);
        }

        GUIManager.open(player, new CustomGameGUI(game, player));
    }

    // --- Target Validation ---

    /**
     * Gets the game a UUID belongs to. Players and bots are looked up directly in entityGames;
     * minions fall back to the game injected into them at spawn. Both {@code getEntityGame}
     * overloads share this resolution so the minion path is uniform (no asymmetric fallback).
     */
    public static Game getEntityGame(UUID uuid) {
        Game direct = entityGames.get(uuid);
        if (direct != null) return direct;

        Hero hero = HeroManager.getHero(uuid);
        if (hero instanceof MinionHero minion) {
            return minion.getGame();
        }
        return null;
    }

    /**
     * Gets the game a LivingEntity belongs to. Delegates to the UUID overload so minions resolve
     * via their injected game regardless of which form a caller uses.
     */
    public static Game getEntityGame(LivingEntity entity) {
        return getEntityGame(entity.getUniqueId());
    }

    /**
     * Returns all living enemies of the given entity in its current game.
     * Throws if the entity is not in a game — intentional for early diagnosis.
     */
    public static List<LivingEntity> getEnemies(LivingEntity entity) {
        return getEntityGame(entity.getUniqueId()).getEnemies(entity.getUniqueId());
    }

    /**
     * Returns all living teammates of the given entity in its current game (excluding itself).
     * Throws if the entity is not in a game — intentional for early diagnosis.
     */
    public static List<LivingEntity> getTeammates(LivingEntity entity) {
        return getEntityGame(entity.getUniqueId()).getTeammates(entity.getUniqueId());
    }

    /**
     * Basic target validity check. Can this entity be targeted at all?
     * Delegates to Hitbox.isValidTarget.
     */
    public static boolean isValidTarget(LivingEntity entity) {
        return Hitbox.isValidTarget(entity);
    }

    /**
     * Checks if an entity is actively playing in a game.
     * Delegates to Hitbox.isAlive.
     */
    public static boolean isAlive(LivingEntity entity) {
        return Hitbox.isAlive(entity);
    }

    /**
     * Full interaction check: can attacker target victim?
     * Both must be alive, in the same game, not self,
     * and passes the friendly fire check.
     * @param overrideFriendlyFire if non-null, overrides the team's friendly fire setting
     */
    public static boolean canTarget(LivingEntity attacker, LivingEntity victim, Boolean overrideFriendlyFire) {
        if (attacker == null || victim == null) return false;
        // Practice range (B24): a practicing player can hit the target dummies — no game involved.
        if (PracticeManager.isPracticeHit(attacker, victim)) return true;
        Game attackerGame = getEntityGame(attacker);
        Game victimGame = getEntityGame(victim);
        if (attackerGame == null || !attackerGame.equals(victimGame)) return false;
        return attackerGame.canTarget(attacker, victim, overrideFriendlyFire);
    }

    /**
     * Convenience overload that uses the team's friendly fire setting.
     */
    public static boolean canTarget(LivingEntity attacker, LivingEntity victim) {
        return canTarget(attacker, victim, null);
    }

    // --- Bot registration ---

    /**
     * Registers a bot (or any non-player entity) UUID into the entityGames map.
     * Used by Game.addBot() since bots don't go through addPlayer().
     */
    public static void registerEntity(UUID uuid, Game game) {
        entityGames.put(uuid, game);
    }

    /**
     * Unregisters a bot (or any non-player entity) UUID from the entityGames map.
     */
    public static void unregisterEntity(UUID uuid) {
        entityGames.remove(uuid);
    }

    // Utility

    public static Game getPlayerGame(Player player) {
        if (player == null) return null;
        return entityGames.get(player.getUniqueId());
    }

    /** Whether the player is mid-respawn in their current game. False for lobby players. */
    public static boolean isRespawning(Player player) {
        Game game = getPlayerGame(player);
        return game != null && game.isRespawning(player.getUniqueId());
    }

    public static boolean isPlayerInGame(Player player) {
        return player != null && entityGames.containsKey(player.getUniqueId());
    }

    /** True when the player is attached to a game purely as a SPECTATOR-team watcher. */
    public static boolean isSpectating(Player player) {
        Game game = getPlayerGame(player);
        return game != null && game.playerColor(player.getUniqueId()) == TeamColor.SPECTATOR;
    }

    public static void clear() {
        new HashSet<>(entityGames.values()).forEach(Game::clean);
        entityGames.clear();
        activeGames.clear();
    }

    public static Set<Game> getActiveGames() {
        return Collections.unmodifiableSet(activeGames);
    }

    public static void addToActive(Game game) {
        activeGames.add(game);
        // Games register in their constructor, before any player is attached; one tick later the
        // roster is in place.
        Bukkit.getScheduler().runTask(Heroes.getInstance(), TabList::refreshAll);
    }

    public static void removeFromActive(Game game) {
        activeGames.remove(game);
        TabList.refreshAll();
    }
}
