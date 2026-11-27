package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.WorldInstances;
import me.stolyy.heroes.game.rules.BossSetup;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.game.rules.WinCondition;
import me.stolyy.heroes.game.settings.GameSettings;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.mechanics.LobbyMobility;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.hero.minion.MinionManager;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.npc.types.GameBotNpc;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.BotConfig;
import me.stolyy.heroes.util.BotNameManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.ErrorReporter;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.glow.TeamGlow;
import me.stolyy.heroes.vfx.ui.visual.BoardDisplay;
import me.stolyy.heroes.vfx.ui.visual.GameVisuals;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Single match lifecycle, roster state, tick loop, and collaborator facade.
 *
 * <p>Roster collection scopes:
 * <ul>
 *   <li>{@code teams} — current team membership (eliminated combatants move to {@link TeamColor#SPECTATOR}).</li>
 *   <li>{@code aliveIds} — non-eliminated, non-spectator combatants used for win checks and targeting.</li>
 *   <li>{@code tickedHeroIds} — all heroes ticked by the match loop, including dormant eliminated ones.</li>
 *   <li>{@code gamePlayers} — full participant state kept for the whole match (survives disconnects for stats).</li>
 *   <li>{@code originalTeams} — team color assigned at {@code STARTING}, used for results, summaries, and combat sides.</li>
 *   <li>{@code gameBots} — bot handles attached to this match.</li>
 * </ul>
 */
public class Game {
    private static final int EMPTY_CHECK_INTERVAL_TICKS = 100;

    /** Outcome of {@link #addRandomBot()} in a custom game lobby. */
    public enum AddBotResult { ADDED, LIMIT_REACHED, NO_NPC_SERVICE, FAILED }

    /** Groups series metadata and completion callback for rounds managed by {@code MatchSeries}. */
    public record SeriesRound(String id, int round, Consumer<Team> onEnd) {}

    private final GameMode gameMode;
    private final GameSettings settings;
    private final PlayerSettings defaultPlayerSettings;
    private final GameVisuals visuals;

    private final MatchRewards rewards;
    private final MatchAnnouncer announcer;
    private final KillCredit credit;
    private final Respawns respawns;
    private final ArenaGuard arena;
    private final CrystalSpawner crystals;
    private final MatchHeroes heroes;
    private final Combatants combatants;

    private final Map<TeamColor, Team> teams = new EnumMap<>(TeamColor.class);
    private final List<BukkitTask> activeTasks = new LinkedList<>();
    private final Set<UUID> aliveIds = new HashSet<>();
    private final Set<UUID> tickedHeroIds = new HashSet<>();
    private final Map<UUID, GameBot> gameBots = new HashMap<>();
    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    private final Map<UUID, TeamColor> originalTeams = new HashMap<>();

    private GameState gameState = GameState.WAITING;
    private GameModeConfig modeConfig = GameModeConfig.CLASSIC;
    private GameModePreset gameModePreset = GameModePreset.DEFAULT;
    private @Nullable BossSetup.Config bossConfig;
    private UUID bossPlayer;
    private @Nullable SeriesRound seriesRound;
    private long startedAt;
    private int secondsLeft;
    private int emptyCheckCounter;

    private Game(GameMap instanceMap, GameMode gameMode) {
        this.gameMode = gameMode;
        this.settings = new GameSettings(Objects.requireNonNull(instanceMap, "instanceMap must not be null"));
        this.defaultPlayerSettings = new PlayerSettings();
        this.visuals = new GameVisuals(this);
        this.rewards = new MatchRewards(this);
        this.announcer = new MatchAnnouncer(this);
        this.credit = new KillCredit(this, rewards, announcer);
        this.respawns = new Respawns(this);
        this.arena = new ArenaGuard(this, respawns);
        this.crystals = new CrystalSpawner(this);
        this.heroes = new MatchHeroes(this, rewards);
        this.combatants = new Combatants(this);

        for (TeamColor color : TeamColor.values()) {
            teams.put(color, new Team(color));
        }
    }

    /** Mints a per-match world instance from {@code template}, constructs the game, and registers it live. */
    public static Game create(GameMap template, GameMode mode) {
        GameMap instance = WorldInstances.createWorld(template);
        Game game = new Game(instance, mode);
        GameManager.registerLive(game);
        return game;
    }

    // === Lifecycle ===

    public void start() {
        if (gameState != GameState.WAITING) {
            throw new IllegalStateException("Cannot start game from state " + gameState);
        }

        GameManager.addToActive(this);
        gameState = GameState.STARTING;
        visuals.showCountdown();

        for (Player p : onlinePlayers(false)) {
            aliveIds.add(p.getUniqueId());
            originalTeams.put(p.getUniqueId(), playerTeam(p.getUniqueId()).color());
        }

        heroes.initPlayers();
        heroes.initBots();

        PlayerVisibility.refreshGlow(this);
        AutoSpectate.onGameStarting(this);

        BukkitTask startTask = Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            if (gameState != GameState.STARTING) return;
            gameState = GameState.IN_PROGRESS;
            startedAt = System.currentTimeMillis();
            secondsLeft = settings.timer();
            visuals.update();

            activeTasks.add(Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
                if (gameState != GameState.IN_PROGRESS) return;
                secondsLeft--;
                if (secondsLeft <= 0) {
                    secondsLeft = 0;
                    end(GameEndReason.TIMEOUT);
                } else {
                    visuals.update();
                }
            }, 20L, 20L));

            for (UUID uuid : new HashSet<>(aliveIds)) {
                Hero hero = HeroManager.getHero(uuid);
                hero.resetStats(hero.getController());
                heroes.startAbilityCooldowns(hero);
            }
            for (UUID uuid : new HashSet<>(aliveIds)) {
                Player p = resolveParticipantEntity(uuid);
                if (p == null) continue;
                GameEffects.applyEffects(p, gamePlayers.get(uuid).settings());
                onHealthChanged(p);
            }
            for (UUID uuid : new HashSet<>(aliveIds)) {
                Player p = resolveParticipantEntity(uuid);
                if (p == null) continue;
                GameEffects.unRestrictPlayer(p);
                if (Interactions.isRealPlayer(p)) p.setAllowFlight(true);
                SpawnProtection.arm(p);
            }
            activeTasks.add(Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), this::gameTick, 0L, 1L));
        }, 20L * GameConfigManager.getCountdownTime());
        activeTasks.add(startTask);
    }

    public void end(GameEndReason reason) {
        end(reason, null);
    }

    public void endWithWinner(Team winner, GameEndReason reason) {
        end(reason, winner);
    }

    private void end(GameEndReason reason, @Nullable Team injectedWinner) {
        if (gameState == GameState.ENDED) return;
        GameState previousState = gameState;
        gameState = GameState.ENDED;

        MinionManager.despawnForGame(this);
        MapSkipPoll.endForGame(this);
        stopMatchTasks();
        if (previousState == GameState.STARTING) {
            for (Player p : onlinePlayers()) {
                GameEffects.unRestrictPlayer(p);
            }
        }

        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (!gamePlayer.respawning()) continue;
            gamePlayer.setRespawning(false);
            Player p = Bukkit.getPlayer(gamePlayer.uuid());
            if (p == null) continue;
            PlayerVisibility.refreshVisibility(p);
            GameEffects.unRestrictPlayer(p);
            parkSpectator(p);
        }
        for (Player p : onlinePlayers()) TeamGlow.clearFor(p);

        Team winningTeam = injectedWinner;
        if (previousState != GameState.WAITING) {
            if (reason == GameEndReason.CANCELLED) {
                Component cancelMsg = GameConfigManager.getMessage("game.force-ended");
                for (Player p : onlinePlayers()) p.sendMessage(cancelMsg);
            } else {
                if (winningTeam == null) {
                    if (reason == GameEndReason.TIMEOUT) {
                        winningTeam = modeConfig.winCondition().timeoutWinner(this);
                    } else {
                        winningTeam = modeConfig.winCondition().winningTeam(this);
                        if (winningTeam == null && reason == GameEndReason.FORFEIT) {
                            List<Team> remaining = teams.values().stream()
                                    .filter(t -> t.color() != TeamColor.SPECTATOR && !t.playerUUIDs().isEmpty())
                                    .toList();
                            if (remaining.size() == 1) winningTeam = remaining.get(0);
                        }
                    }
                }

                if (winningTeam != null) {
                    announcer.playWinSound(winningTeam);
                    Map<String, String> endPlaceholders = announcer.buildEndPlaceholders(winningTeam);
                    visuals.win(winningTeam, endPlaceholders);
                    announcer.sendSummaryToLosers(winningTeam, endPlaceholders);
                } else {
                    visuals.draw();
                }
                rewards.recordWinLoss(winningTeam);
                MatchLog.write(this, reason, winningTeam);
            }
        }

        BoardDisplay.refreshAll();

        GameManager.removeFromActive(this);
        if (seriesRound != null) {
            seriesRound.onEnd().accept(winningTeam);
        } else {
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::clean, 20L * GameConfigManager.getCleanupDelay());
        }
    }

    private void stopMatchTasks() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        visuals.cancelTasks();
        crystals.clear();
    }

    public boolean canStart() {
        if (gameState != GameState.WAITING) return false;
        long teamsWithParticipants = teams.values().stream()
                .filter(t -> t.color() != TeamColor.SPECTATOR)
                .filter(t -> !t.playerUUIDs().isEmpty())
                .count();
        boolean hasRealPlayer = teams.values().stream()
                .filter(t -> t.color() != TeamColor.SPECTATOR)
                .flatMap(t -> t.playerUUIDs().stream())
                .anyMatch(uuid -> Bukkit.getPlayer(uuid) != null);
        return teamsWithParticipants >= 2 && hasRealPlayer;
    }

    public boolean checkEnd() {
        if (gameState == GameState.ENDED) return true;

        WinCondition winCondition = modeConfig.winCondition();
        if (winCondition.isMet(this)) {
            Team winner = winCondition.winningTeam(this);
            GameEndReason reason = (winCondition == WinCondition.KILL_TARGET && winner != null
                    && modeConfig.killLimit() > 0 && WinCondition.teamKills(this, winner.color()) >= modeConfig.killLimit())
                    ? GameEndReason.KILL_TARGET
                    : GameEndReason.LAST_TEAM_STANDING;
            if (winner != null) endWithWinner(winner, reason);
            else end(reason);
            return true;
        }
        return false;
    }

    private void gameTick() {
        if (gameState != GameState.IN_PROGRESS) return;

        arena.enforceBounds();

        if (++emptyCheckCounter >= EMPTY_CHECK_INTERVAL_TICKS) {
            emptyCheckCounter = 0;
            if (onlinePlayers().isEmpty()) {
                end(GameEndReason.FORFEIT);
                return;
            }
        }

        respawns.tick();

        for (UUID uuid : new HashSet<>(tickedHeroIds)) {
            Hero hero = HeroManager.getHero(uuid);
            if (hero == null) {
                tickedHeroIds.remove(uuid);
                continue;
            }
            Controller controller = hero.getController();
            if (controller == null || controller.getEntity() == null) {
                MinionManager.despawnAll(hero);
                HeroManager.removeHero(uuid);
                tickedHeroIds.remove(uuid);
                if (isBotParticipant(uuid)) {
                    ErrorReporter.report(this, "Bot " + uuid + " lost its entity mid-game — removing from match");
                    removeBot(uuid);
                }
                continue;
            }
            // Per-entity catch prevents one erroring hero from aborting remaining ticks and bounds/crystal steps.
            try {
                hero.tick();
                if (!controller.isPlayer() && !hero.isEliminated()) {
                    BotController bot = (BotController) controller;
                    bot.ensureCombatState(hero);
                    bot.updateGroundState(hero);
                    bot.tickAI(hero);
                }
            } catch (Exception e) {
                ErrorReporter.report(this, "Hero tick failed for " + uuid, e);
            }
        }

        for (MinionHero minion : minions()) {
            Controller mc = minion.getController();
            if (mc == null) {
                throw new IllegalStateException("Active minion " + minion.getName() + " in game " + settings.map().name() + " has no controller");
            }
            // Per-entity catch mirrors the hero tick loop above.
            try {
                minion.tick();
                if (mc instanceof BotController bot) bot.tickAI(minion);
            } catch (Exception e) {
                ErrorReporter.report(this, "Minion tick failed for " + mc.getUniqueId(), e);
            }
        }

        Set<Player> alivePlayers = new HashSet<>();
        for (UUID uuid : new HashSet<>(aliveIds)) {
            Player p = resolveParticipantEntity(uuid);
            if (p != null) alivePlayers.add(p);
        }

        crystals.tick(alivePlayers);
        arena.unstickTrappedPlayers(alivePlayers);
    }

    public void clean() {
        for (UUID uuid : getAllPlayerUUIDs()) {
            if (GameManager.getEntityGame(uuid) != this) {
                for (Team team : teams.values()) team.remove(uuid);
                aliveIds.remove(uuid);
                continue;
            }
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                removePlayer(p);
                Heroes.teleportToLobby(p);
            }
        }
        for (Map.Entry<UUID, GameBot> entry : new HashMap<>(gameBots).entrySet()) {
            UUID botUuid = entry.getKey();
            if (GameManager.getEntityGame(botUuid) != this) continue;
            NpcServiceProvider.get().remove(entry.getValue().getNpcHandle().id());
            HeroManager.removeHero(botUuid);
            GameManager.unregisterEntity(botUuid);
        }
        gameBots.clear();
        tickedHeroIds.clear();
        cleanupWorld();
    }

    public void cleanupWorld() {
        stopMatchTasks();
        WorldInstances.deleteWorld(settings.map());
        GameManager.unregisterLive(this);
    }

    // === Roster & Match Transfers ===

    public void addPlayer(Player player, TeamColor teamColor) {
        PracticeManager.exitPractice(player, false);
        LobbyMobility.reset(player);

        if (gameState != GameState.WAITING) teamColor = TeamColor.SPECTATOR;

        UUID uuid = player.getUniqueId();
        teams.get(teamColor).add(uuid);
        // Register entity ownership BEFORE refreshVisibility so a new spectator isn't hidden against null.
        GameManager.registerEntity(uuid, this);

        GamePlayer existing = gamePlayers.get(uuid);
        if (existing == null) {
            GamePlayer created = new GamePlayer(uuid, teamColor, new PlayerSettings(defaultPlayerSettings), null);
            if (modeConfig.startHp() > 0) created.settings().setHealth(modeConfig.startHp());
            gamePlayers.put(uuid, created);
        } else {
            existing.setTeam(teamColor);
            existing.markRejoined();
        }

        if (teamColor == TeamColor.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        } else {
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            GameEffects.restrictPlayer(player);
        }

        respawns.teleportToSpawn(uuid, teamColor);
        PlayerVisibility.refreshVisibility(player);
        rosterChanged();

        if (teamColor != TeamColor.SPECTATOR) AutoSpectate.onJoinedGame(player);
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Team team = playerTeam(uuid);
        if (team == null) return;

        if (gameState == GameState.IN_PROGRESS
                && team.color() != TeamColor.SPECTATOR
                && aliveIds.contains(uuid)) {
            credit.creditLeaveAsKill(player);
        }

        gamePlayers.get(uuid).setRespawning(false);
        team.remove(uuid);
        detachParticipant(player);
        GameManager.unregisterEntity(uuid);
        PlayerVisibility.refreshVisibility(player);

        MapSkipPoll.endForLeader(uuid);
        rosterChanged();
        if (gameState == GameState.IN_PROGRESS || gameState == GameState.STARTING) checkEnd();
    }

    /**
     * Removes a player when transferring wholesale to a successor game (no teleport, no end check).
     * Shared {@link #detachParticipant} also despawns minions cleanly between rounds.
     */
    public void silentRemove(Player player) {
        Team team = playerTeam(player.getUniqueId());
        if (team == null) return;

        team.remove(player.getUniqueId());
        detachParticipant(player);
    }

    private void detachParticipant(Player player) {
        UUID uuid = player.getUniqueId();
        gamePlayers.get(uuid).markLeft();
        aliveIds.remove(uuid);
        tickedHeroIds.remove(uuid);

        Hero leavingHero = HeroManager.getHero(uuid);
        if (leavingHero != null) MinionManager.despawnAll(leavingHero);
        HeroManager.removePlayer(player);
        GameEffects.removeEffects(player);
        GameEffects.unRestrictPlayer(player);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        TeamGlow.clearFor(player);
    }

    public boolean addBot(GameBot bot, TeamColor teamColor) {
        if (gameState != GameState.WAITING) return false;
        Team team = teams.get(teamColor);

        LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(bot.getNpcHandle());
        if (!(entity instanceof Player botEntity)) {
            ErrorReporter.report(this, "Cannot add bot " + bot.getName() + ": NPC has no spawned Player entity");
            return false;
        }

        UUID botUUID = botEntity.getUniqueId();
        team.add(botUUID);
        gameBots.put(botUUID, bot);
        GamePlayer botGamePlayer = new GamePlayer(botUUID, teamColor, new PlayerSettings(defaultPlayerSettings), bot);
        if (modeConfig.startHp() > 0) botGamePlayer.settings().setHealth(modeConfig.startHp());
        gamePlayers.put(botUUID, botGamePlayer);
        GameManager.registerEntity(botUUID, this);
        respawns.teleportToSpawn(botUUID, teamColor);
        rosterChanged();
        PlayerVisibility.refreshBotListing(botEntity);
        return true;
    }

    /** Spawns and attaches a randomly named, bot-eligible hero bot to the smallest playable team. */
    public AddBotResult addRandomBot() {
        if (gameBots.size() >= GameConfigManager.getMaxBotsPerGame()) {
            return AddBotResult.LIMIT_REACHED;
        }

        Set<String> usedNames = new HashSet<>();
        for (GameBot existing : gameBots.values()) {
            usedNames.add(existing.getName());
        }
        String botName = BotNameManager.getRandomName(usedNames);

        List<String> eligible = BotConfig.eligibleHeroes();
        String heroName = eligible.get(new Random().nextInt(eligible.size()));

        if (settings == null || settings.map() == null || settings.map().spawnLocations().isEmpty()) {
            throw new IllegalStateException("Map has no spawn locations for bot " + botName);
        }
        Location spawnLoc = settings.map().spawnLocations().get(0);

        GameBotNpc npc = new GameBotNpc(botName, spawnLoc);
        npc.spawn();
        if (npc.getHandle() == null) {
            return AddBotResult.NO_NPC_SERVICE;
        }

        GameBot bot = new GameBot(botName, heroName, npc.getHandle());
        if (!addBot(bot, smallestPlayableTeam())) {
            NpcServiceProvider.get().remove(npc.getHandle().id());
            return AddBotResult.FAILED;
        }
        return AddBotResult.ADDED;
    }

    private TeamColor smallestPlayableTeam() {
        TeamColor smallest = TeamColor.RED;
        int minSize = Integer.MAX_VALUE;
        for (Map.Entry<TeamColor, Team> entry : teams.entrySet()) {
            TeamColor color = entry.getKey();
            if (color == TeamColor.SPECTATOR) continue;
            int size = (int) entry.getValue().playerUUIDs().stream()
                    .filter(uuid -> Bukkit.getPlayer(uuid) != null || gameBots.containsKey(uuid))
                    .count();
            if (size < minSize) {
                minSize = size;
                smallest = color;
            }
        }
        return smallest;
    }

    public void removeBot(UUID botUUID) {
        for (Team team : teams.values()) team.remove(botUUID);
        GamePlayer gamePlayer = gamePlayers.get(botUUID);
        if (gamePlayer != null) gamePlayer.markLeft();
        gameBots.remove(botUUID);
        GameManager.unregisterEntity(botUUID);
        HeroManager.removeHero(botUUID);
        aliveIds.remove(botUUID);
        tickedHeroIds.remove(botUUID);
        if (gameState == GameState.IN_PROGRESS || gameState == GameState.STARTING) checkEnd();
    }

    public void changeBotTeam(UUID botUUID, TeamColor teamColor) {
        Team oldTeam = playerTeam(botUUID);
        if (oldTeam == null || oldTeam.color() == teamColor) return;
        oldTeam.remove(botUUID);
        teams.get(teamColor).add(botUUID);
        gamePlayers.get(botUUID).setTeam(teamColor);
        respawns.teleportToSpawn(botUUID, teamColor);
        rosterChanged();
    }

    public void changeTeam(Player player, TeamColor teamColor) {
        Team oldTeam = playerTeam(player.getUniqueId());
        if (oldTeam == null || oldTeam.color() == teamColor) return;
        oldTeam.remove(player.getUniqueId());

        teams.get(teamColor).add(player.getUniqueId());
        gamePlayers.get(player.getUniqueId()).setTeam(teamColor);

        if (teamColor == TeamColor.SPECTATOR) {
            GameEffects.unRestrictPlayer(player);
            parkSpectator(player);
        } else {
            if (gameState == GameState.WAITING) GameEffects.restrictPlayer(player);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            respawns.teleportToSpawn(player.getUniqueId(), teamColor);
        }
        rosterChanged();
    }

    /** Moves a participant (online player or game bot) to {@code target}. */
    public void changeParticipantTeam(UUID uuid, TeamColor target) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) changeTeam(p, target);
        else if (gameBots.containsKey(uuid)) changeBotTeam(uuid, target);
    }

    private void rosterChanged() {
        visuals.update();
        PlayerVisibility.refreshGlow(this);
    }

    /** Creates a successor match on {@code newMap} carrying settings, mode config, preset, and boss. */
    public Game createSuccessor(GameMap newMap) {
        Game next = Game.create(newMap, gameMode);
        next.settings().copy(settings);
        next.defaultPlayerSettings().copy(defaultPlayerSettings);
        next.setGameModePreset(gameModePreset);
        next.updateModeConfig(ignored -> modeConfig);
        if (bossConfig != null) next.updateBossConfig(ignored -> bossConfig);
        next.setBossPlayer(bossPlayer);
        return next;
    }

    /** Transfers a human participant from this game into {@code next}, copying settings and hero override. */
    public void transferPlayerTo(Game next, Player p, TeamColor team, @Nullable String heroOverride) {
        GamePlayer oldState = gamePlayers.get(p.getUniqueId());
        silentRemove(p);
        next.addPlayer(p, team);
        GamePlayer newState = next.getGamePlayer(p.getUniqueId());
        newState.settings().copy(oldState.settings());
        if (heroOverride != null) newState.setHeroOverride(heroOverride);
    }

    /** Re-spawns every bot from this game into {@code next} so world unload cannot orphan Citizens NPCs. */
    public void transferBotsTo(Game next) {
        for (Map.Entry<UUID, GameBot> entry : new ArrayList<>(gameBots.entrySet())) {
            UUID oldUUID = entry.getKey();
            GameBot bot = entry.getValue();
            String name = bot.getName();
            String heroName = bot.getHeroName();
            int tier = bot.getDifficultyTier();
            TeamColor teamColor = playerColor(oldUUID);
            PlayerSettings oldSettings = gamePlayers.get(oldUUID).settings();
            removeBot(oldUUID);
            NpcServiceProvider.get().remove(bot.getNpcHandle().id());

            GameBotNpc npc = new GameBotNpc(name, next.settings().map().spawnLocations().get(0));
            npc.spawn();
            if (npc.getHandle() == null) {
                throw new IllegalStateException("Could not respawn bot " + name + " in map " + next.settings().map().name());
            }

            boolean attached = false;
            try {
                GameBot fresh = new GameBot(name, heroName, npc.getHandle());
                fresh.setDifficultyTier(tier);
                if (!next.addBot(fresh, teamColor)) {
                    throw new IllegalStateException("Could not carry bot " + name + " into map " + next.settings().map().name());
                }
                UUID newUUID = NpcServiceProvider.get().getBukkitEntity(fresh.getNpcHandle()).getUniqueId();
                next.getGamePlayer(newUUID).settings().copy(oldSettings);
                attached = true;
            } finally {
                if (!attached) NpcServiceProvider.get().remove(npc.getHandle().id());
            }
        }
    }

    // === Death, Elimination, Respawn ===

    /**
     * Authoritative death entry point for both real players and NPCs.
     * Callers: {@code GameListener.onPlayerDeath}, {@link #handleEnvironmentalDamage} (bots),
     * {@code Interactions.applyDamage} (lethal hit on NPC participant), and {@link ArenaGuard#enforceBounds} (bots).
     */
    public void handleDeath(Player player, @Nullable LivingEntity killer) {
        if (isRespawning(player.getUniqueId()) || gameState != GameState.IN_PROGRESS) return;

        if (HeroManager.getHero(player) instanceof MinionHero minion) {
            credit.clear(player.getUniqueId());
            Bukkit.getScheduler().runTask(Heroes.getInstance(), minion::despawn);
            return;
        }

        Team team = playerTeam(player.getUniqueId());
        if (team == null || team.color() == TeamColor.SPECTATOR) return;

        GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
        gamePlayer.addDeath();
        if (modeConfig.deathOutcome().consumesLife()) gamePlayer.subtractLife();

        Location deathLocation = player.getLocation().clone();
        if (Interactions.isRealPlayer(player)) PlayerStatsManager.getStats(player).recordDeath();

        Hero hero = HeroManager.getHero(player);
        if (killer == null) killer = credit.resolveFallbackKiller(player.getUniqueId());

        if (killer != null) {
            GamePlayer killerGp = credit.recordKill(killer.getUniqueId(), player, killer.getName(), false);
            if (killerGp != null) {
                modeConfig.onKill().apply(killerGp, this);
                if (checkEnd()) {
                    rescueVictimOnMatchEnd(player);
                    return;
                }
            }
        } else {
            announcer.broadcastDeath(player);
        }

        if (modeConfig.deathOutcome().shouldEliminate(gamePlayer)) {
            eliminateParticipant(player, hero, deathLocation);
        } else {
            beginRespawn(player, gamePlayer, hero, killer, deathLocation);
        }
        visuals.update();
        checkEnd();
    }

    private void rescueVictimOnMatchEnd(Player player) {
        restoreToMaxHealth(player);
        parkSpectator(player);
        visuals.update();
    }

    private void eliminateParticipant(Player player, Hero hero, Location deathLocation) {
        aliveIds.remove(player.getUniqueId());
        restoreToMaxHealth(player);
        changeTeam(player, TeamColor.SPECTATOR);
        credit.clear(player.getUniqueId());
        player.sendMessage(GameConfigManager.getMessage("game.eliminated"));
        hero.eliminate(deathLocation);
    }

    private void beginRespawn(Player player, GamePlayer gamePlayer, Hero hero,
                              @Nullable LivingEntity killer, Location deathLocation) {
        double maxHp = restoreToMaxHealth(player);
        onHealthChanged(player, (int) maxHp);

        int respawnSeconds = GameConfigManager.getRespawnTime();
        gamePlayer.setRespawning(true);
        gamePlayer.setRespawnTicksLeft(20 * respawnSeconds);

        respawns.teleportParticipant(player.getUniqueId(), player,
                respawns.getRespawnLocation(player, killer, deathLocation));
        PlayerVisibility.refreshVisibility(player);
        PlayerVisibility.refreshGlow(this);

        GameEffects.restrictPlayer(player);
        player.setAllowFlight(false);
        modeConfig.onRespawn().onCountdownStart(player, this);

        visuals.showRespawnTick(player, respawnSeconds);
        hero.triggerDeath(deathLocation);
    }

    private double restoreToMaxHealth(Player player) {
        double maxHp = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        player.setHealth(maxHp);
        return maxHp;
    }

    // === Public Match API ===

    public void parkSpectator(Player player) {
        if (isBotParticipant(player.getUniqueId())) {
            NpcServiceProvider.get().stopNavigation(gamePlayers.get(player.getUniqueId()).bot().getNpcHandle());
        }
        respawns.teleportParticipant(player.getUniqueId(), player, settings.map().spectatorLocation());
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
    }

    public boolean outsideArena(Location location) { return arena.outsideArena(location); }
    public void killOutOfBounds(Player player) { arena.killOutOfBounds(player); }

    public void handleEnvironmentalDamage(Player player, EntityDamageEvent event) {
        if (gameState != GameState.IN_PROGRESS || isRespawning(player.getUniqueId())
                || !isBotParticipant(player.getUniqueId())) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() <= Interactions.ZERO_HP_BUFFER) {
            event.setCancelled(true);
            player.setHealth(Interactions.ZERO_HP_BUFFER);
            handleDeath(player, null);
        }
    }

    public List<MinionHero> minions() {
        return MinionManager.getActiveMinions().stream()
                .filter(m -> m.getGame() == this)
                .toList();
    }

    public List<MinionHero> minionsOf(TeamColor color) {
        return minions().stream()
                .filter(m -> m.getTeamColor() == color)
                .toList();
    }

    public void onMinionSpawned() { rosterChanged(); }
    public Location activeCrystalLocation() { return crystals.activeCrystalLocation(); }
    public Location getRespawnLocation(Player player, @Nullable LivingEntity killer, Location deathLocation) {
        return respawns.getRespawnLocation(player, killer, deathLocation);
    }
    public void swapHero(GamePlayer gamePlayer, String heroName) { heroes.swapHero(gamePlayer, heroName); }

    public void onHealthChanged(Player player) { visuals.updateHealthScore(player, (int) player.getHealth()); }
    private void onHealthChanged(Player player, int hp) { visuals.updateHealthScore(player, hp); }
    public void restoreHealth(Player player, double health) {
        double max = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        double clamped = Math.min(health, max);
        player.setHealth(clamped);
        visuals.updateHealthScore(player, (int) clamped);
    }

    public void updateModeConfig(UnaryOperator<GameModeConfig> update) {
        GameModeConfig next = update.apply(modeConfig);
        boolean startHpChanged = this.modeConfig.startHp() != next.startHp();
        this.modeConfig = next;
        if (startHpChanged && gameState == GameState.WAITING) pushStartHpToSettings();
    }

    public void setGameModePreset(GameModePreset preset) {
        this.gameModePreset = preset;
        this.bossConfig = preset == GameModePreset.BOSS ? BossSetup.Config.fromYml() : null;
        updateModeConfig(ignored -> preset.buildConfig());
    }

    public BossSetup.Config bossConfig() {
        if (bossConfig == null) bossConfig = BossSetup.Config.fromYml();
        return bossConfig;
    }

    public void updateBossConfig(UnaryOperator<BossSetup.Config> update) {
        this.bossConfig = update.apply(bossConfig());
    }

    private void pushStartHpToSettings() {
        double hp = modeConfig.startHp() > 0
                ? modeConfig.startHp()
                : Configs.requireDouble(GameConfigManager.getGameConfig(), "defaults.team.health");
        for (GamePlayer gp : gamePlayers.values()) {
            gp.settings().setHealth(hp);
        }
    }

    /** Applies one settings change to every active {@link GamePlayer} currently on {@code teamColor}. */
    public void applySettings(TeamColor teamColor, Consumer<PlayerSettings> change) {
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (gamePlayer.leftGame() || gamePlayer.team() != teamColor) continue;
            change.accept(gamePlayer.settings());
        }
    }

    /** Applies one settings change to game defaults and all active {@link GamePlayer}s (spectators included). */
    public void applySettingsAll(Consumer<PlayerSettings> change) {
        change.accept(defaultPlayerSettings);
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (gamePlayer.leftGame()) continue;
            change.accept(gamePlayer.settings());
        }
    }

    // === Target & Roster Queries ===

    public boolean isAlive(LivingEntity entity) { return combatants.isAlive(entity); }
    public boolean canTarget(LivingEntity attacker, LivingEntity victim, @Nullable Boolean overrideFriendlyFire) {
        return combatants.canTarget(attacker, victim, overrideFriendlyFire);
    }
    public @Nullable TeamColor combatColor(UUID uuid) { return combatants.combatColor(uuid); }
    public List<LivingEntity> getEnemies(UUID entityUUID) { return combatants.getEnemies(entityUUID); }

    public boolean friendlyFire(UUID uuid) { return credit.resolveGamePlayer(uuid).settings().friendlyFire(); }
    public boolean ultimateEnabled(UUID uuid) { return credit.resolveGamePlayer(uuid).settings().ultimatesEnabled(); }
    public TeamColor playerColor(UUID uuid) { return playerTeam(uuid).color(); }
    public TeamColor originalColor(UUID uuid) { return originalTeams.get(uuid); }
    public boolean isRespawning(UUID uuid) {
        GamePlayer gp = gamePlayers.get(uuid);
        return gp != null && gp.respawning();
    }
    public boolean isBotParticipant(UUID uuid) {
        GamePlayer gp = gamePlayers.get(uuid);
        return gp != null && gp.isBot();
    }

    public KillCredit credit() { return credit; }
    public String participantName(GamePlayer gp) { return announcer.participantName(gp); }
    public @Nullable String liveEntityName(UUID uuid) { return announcer.liveEntityName(uuid); }
    public int rosterSize() { return teams.values().stream().mapToInt(t -> t.playerUUIDs().size()).sum(); }
    public Set<UUID> members(TeamColor color) { return teams.get(color).playerUUIDs(); }
    public int secondsLeft() { return secondsLeft; }

    public GameState gameState() { return gameState; }
    public GameMode gameMode() { return gameMode; }
    public GameSettings settings() { return settings; }
    public PlayerSettings defaultPlayerSettings() { return defaultPlayerSettings; }
    public GameModeConfig modeConfig() { return modeConfig; }
    public GameModePreset gameModePreset() { return gameModePreset; }
    public UUID bossPlayer() { return bossPlayer; }
    public void setBossPlayer(UUID bossPlayer) { this.bossPlayer = bossPlayer; }
    public long durationMs() { return startedAt == 0 ? 0 : System.currentTimeMillis() - startedAt; }
    public @Nullable SeriesRound seriesRound() { return seriesRound; }
    public void setSeriesRound(@Nullable SeriesRound seriesRound) { this.seriesRound = seriesRound; }

    public GamePlayer getGamePlayer(UUID uuid) { return gamePlayers.get(uuid); }
    public Map<UUID, GamePlayer> getGamePlayers() { return Collections.unmodifiableMap(gamePlayers); }
    public Set<UUID> getActivePlayers() { return Collections.unmodifiableSet(aliveIds); }
    public Map<TeamColor, Team> getTeams() { return Collections.unmodifiableMap(teams); }
    public Map<UUID, GameBot> getGameBots() { return Collections.unmodifiableMap(gameBots); }

    public Set<Player> onlinePlayers() { return onlinePlayers(true); }
    public Set<Player> onlinePlayers(boolean includeSpectators) {
        return teams.values().stream()
                .filter(team -> includeSpectators || team.color() != TeamColor.SPECTATOR)
                .flatMap(team -> team.onlinePlayers().stream())
                .collect(Collectors.toSet());
    }

    public @Nullable Team playerTeam(UUID uuid) {
        for (Team team : teams.values()) {
            if (team.contains(uuid)) return team;
        }
        Hero hero = HeroManager.getHero(uuid);
        if (hero instanceof MinionHero minion && minion.getTeamColor() != null) {
            return teams.get(minion.getTeamColor());
        }
        return null;
    }

    // === Package-private Collaborator Access ===

    GameVisuals visuals() { return visuals; }
    Map<TeamColor, Team> teamMap() { return teams; }
    Set<UUID> aliveIds() { return aliveIds; }
    Set<UUID> tickedHeroIds() { return tickedHeroIds; }
    Map<UUID, GameBot> botMap() { return gameBots; }
    Map<UUID, TeamColor> originalTeamMap() { return originalTeams; }

    List<Player> botEntities() {
        List<Player> out = new ArrayList<>();
        for (UUID uuid : gameBots.keySet()) {
            Player p = resolveParticipantEntity(uuid);
            if (p != null) out.add(p);
        }
        return out;
    }

    @Nullable Player resolveParticipantEntity(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player;
        GamePlayer gp = gamePlayers.get(uuid);
        if (gp == null || !gp.isBot()) return null;
        return NpcServiceProvider.get().getBukkitEntity(gp.bot().getNpcHandle())
                instanceof Player botEntity && botEntity.getUniqueId().equals(uuid) ? botEntity : null;
    }

    private Set<UUID> getAllPlayerUUIDs() {
        return teams.values().stream()
                .flatMap(team -> team.playerUUIDs().stream())
                .collect(Collectors.toSet());
    }
}
