package me.stolyy.heroes.game.series;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.map.MapPool;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroSelectGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

/**
 * Orchestrates a series of Game rounds (Nuzlocke/Ironman, A6.2). Adopts an
 * already-arranged WAITING custom game as round 1; between rounds players STAY in the
 * finished world while picking their next hero (no lobby detour), then transfer directly
 * into the next round's freshly instanced world — two worlds briefly coexist.
 *
 * <p>Hero marking is mode-branched: Nuzlocke marks the ELIMINATED players' heroes dead
 * (with HP carry-over for survivors); Ironman marks the WINNING team's heroes cleared,
 * and clearing the target count wins the series outright.
 *
 * <p>GameManager never learns about series — players register in entityGames per-round
 * through normal addPlayer; this class only orchestrates lifecycle.
 */
public class MatchSeries {

    private final GameModePreset preset;
    private final GameModeConfig config;
    private final GameMode gameMode;
    private GameMap currentMap; // rotates every round
    private final List<UUID> participants = new ArrayList<>();
    // Nuzlocke players who ran out of heroes: no longer participants (excluded from picks,
    // teams, and win counting) but transferred into every remaining round as SPECTATOR.
    private final Set<UUID> eliminatedSpectators = new LinkedHashSet<>();
    private final Map<UUID, TeamColor> seriesTeams = new HashMap<>();
    private final Map<UUID, SeriesPlayerState> playerStates = new HashMap<>();
    private final List<String> heroPool; // frozen at series start
    private final int heroTarget;        // 'all' resolved against the frozen pool

    private Game currentGame;
    private int roundNumber = 1;
    private final String seriesId = java.util.UUID.randomUUID().toString().substring(0, 8);
    private boolean seriesOver;
    private final Map<TeamColor, Integer> roundWins = new HashMap<>(); // plain SERIES tally

    private final Set<UUID> pendingPicks = new HashSet<>();
    private final Map<UUID, String> picks = new HashMap<>();
    private BukkitTask pickWatchdog;
    private int pickSeconds;             // seconds elapsed in the current pick phase
    private final int pickTimeout;       // 0 = wait for the stragglers forever
    private final int waitingTitleDelay;

    /**
     * {@code pendingPicks} alone cannot express readiness: it is empty BOTH when everyone has
     * picked and when no pick phase is running at all. A click arriving from a stale (ghost-
     * reopened) pick GUI therefore read as "everyone is ready" and started a whole second round —
     * a new Game and a new world — on top of the one already running. The phase disambiguates the
     * two states and makes {@link #startNextRound()} re-entrancy-safe on its own.
     */
    private enum Phase { ROUND, PICKING, TRANSITIONING, OVER }
    private Phase phase = Phase.ROUND;

    /** Adopts the arranged WAITING game as round 1 and starts it. Bots are rejected upstream. */
    public MatchSeries(Game setupGame) {
        this.preset = setupGame.gameModePreset();
        this.config = setupGame.modeConfig();
        this.gameMode = setupGame.gameMode();
        this.currentMap = setupGame.settings().map();
        this.currentGame = setupGame;
        setupGame.setSeriesInfo(seriesId, roundNumber);
        this.heroPool = List.copyOf(HeroConfig.getAvailableNames());
        this.heroTarget = resolveHeroTarget(config.heroesToEliminate());
        this.pickTimeout = Configs.requireInt(GameConfigManager.getGameConfig(), "series.pick-timeout");
        this.waitingTitleDelay = Configs.requireInt(GameConfigManager.getGameConfig(), "series.waiting-title-delay");

        for (Team team : setupGame.getTeams().values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;
            for (UUID uuid : team.playerUUIDs()) {
                if (Bukkit.getPlayer(uuid) == null) continue;
                participants.add(uuid);
                seriesTeams.put(uuid, team.color());
                playerStates.put(uuid, new SeriesPlayerState(uuid));
                // stocks 0 (plain SERIES) keeps each player's own lives setting.
                if (config.stocks() > 0) setupGame.getGamePlayer(uuid).settings().setLives(config.stocks());
            }
        }

        setupGame.setRoundEndCallback(this::onRoundEnd);
        setupGame.start();
        broadcast(Component.text(preset.displayName() + " series started — round 1!", NamedTextColor.AQUA));
    }

    public Game currentGame() {
        return currentGame;
    }

    public SeriesPlayerState getState(UUID uuid) {
        return playerStates.get(uuid);
    }

    private int resolveHeroTarget(String configured) {
        if (configured == null || configured.equalsIgnoreCase("all")) return heroPool.size();
        return Math.max(1, Math.min(heroPool.size(), Integer.parseInt(configured)));
    }

    // === Round lifecycle ===

    /** Fired synchronously from Game.end() with the round winner (null = draw). */
    private void onRoundEnd(Team winner) {
        if (seriesOver) return;
        Game round = currentGame;

        // Leavers and offline players are dropped from the series.
        participants.removeIf(uuid -> {
            GamePlayer gamePlayer = round.getGamePlayer(uuid);
            boolean gone = gamePlayer == null || gamePlayer.leftGame() || Bukkit.getPlayer(uuid) == null;
            if (gone) {
                pendingPicks.remove(uuid);
                picks.remove(uuid);
            }
            return gone;
        });

        // Plain best-of-X series: no hero elimination or HP snapshots — tally the round,
        // end at the majority, otherwise re-pick (optional) and roll the next round.
        if (preset == GameModePreset.SERIES) {
            if (winner != null) roundWins.merge(winner.color(), 1, Integer::sum);
            broadcast(seriesScore());
            int target = config.bestOf() / 2 + 1;
            if (winner != null && roundWins.getOrDefault(winner.color(), 0) >= target) {
                endSeries(winner.color());
                return;
            }
            if (checkSeriesCollapse()) return;
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
                if (seriesOver) return;
                if (config.repickBetweenRounds()) {
                    beginPicks();
                } else {
                    // Keep current heroes: pre-fill the picks and go straight to the next round.
                    for (UUID uuid : participants) {
                        picks.put(uuid, currentGame.getGamePlayer(uuid).currentHeroName());
                    }
                    startNextRound();
                }
            }, 20L * GameConfigManager.getCleanupDelay());
            return;
        }

        // Snapshot + mark (A6.4 + mode rules). HP snapshots store ACTUAL bukkit health, so
        // the 0-HP buffer (0.5 actual) round-trips without special cases.
        for (UUID uuid : participants) {
            GamePlayer gamePlayer = round.getGamePlayer(uuid);
            SeriesPlayerState state = playerStates.get(uuid);
            String hero = gamePlayer.currentHeroName();
            boolean survived = round.getActivePlayers().contains(uuid);
            if (preset == GameModePreset.IRONMAN) {
                // Winning clears the hero; losing on a hero does NOT spend it.
                if (winner != null && seriesTeams.get(uuid) == winner.color()) {
                    state.markDead(hero);
                }
            } else { // NUZLOCKE
                if (survived) {
                    state.setHeroHp(hero, Bukkit.getPlayer(uuid).getHealth());
                } else {
                    state.markDead(hero);
                }
            }
        }

        // Ironman series win: cleared the target count ("out of heroes" IS the win).
        // team-scope: EVERY member of a team must clear before that team takes the series.
        if (preset == GameModePreset.IRONMAN) {
            if (config.seriesTeamScope()) {
                Map<TeamColor, List<UUID>> byTeam = new HashMap<>();
                for (UUID uuid : participants) {
                    byTeam.computeIfAbsent(seriesTeams.get(uuid), k -> new ArrayList<>()).add(uuid);
                }
                for (Map.Entry<TeamColor, List<UUID>> entry : byTeam.entrySet()) {
                    boolean allCleared = !entry.getValue().isEmpty() && entry.getValue().stream()
                            .allMatch(uuid -> playerStates.get(uuid).deadHeroes().size() >= heroTarget);
                    if (allCleared) {
                        endSeries(entry.getKey());
                        return;
                    }
                }
            } else {
                for (UUID uuid : participants) {
                    if (playerStates.get(uuid).deadHeroes().size() >= heroTarget) {
                        endSeries(seriesTeams.get(uuid));
                        return;
                    }
                }
            }
        }

        // Nuzlocke: players with no playable heroes left stop competing but stay with the
        // series as spectators (they were already flipped to SPECTATOR by their elimination
        // in the round that just ended).
        if (preset == GameModePreset.NUZLOCKE) {
            List<UUID> outOfHeroes = new ArrayList<>(participants.stream()
                    .filter(uuid -> playerStates.get(uuid).deadHeroes().size() >= heroTarget)
                    .toList());
            // team-scope: one player running dry takes their WHOLE team out of the series.
            if (config.seriesTeamScope() && !outOfHeroes.isEmpty()) {
                Set<TeamColor> deadTeams = new HashSet<>();
                for (UUID uuid : outOfHeroes) deadTeams.add(seriesTeams.get(uuid));
                for (UUID uuid : participants) {
                    if (deadTeams.contains(seriesTeams.get(uuid)) && !outOfHeroes.contains(uuid)) {
                        outOfHeroes.add(uuid);
                    }
                }
            }
            for (UUID uuid : outOfHeroes) {
                participants.remove(uuid);
                pendingPicks.remove(uuid);
                picks.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    eliminatedSpectators.add(uuid);
                    p.sendMessage(Component.text("You are out of heroes! Spectating the rest of the series.",
                            NamedTextColor.RED));
                }
            }
        }

        if (checkSeriesCollapse()) return;

        // Let the round's win screen play out, then open the between-round picks.
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::beginPicks,
                20L * GameConfigManager.getCleanupDelay());
    }

    // === Between-round picks ===

    private void beginPicks() {
        if (seriesOver) return;
        phase = Phase.PICKING;
        pendingPicks.clear();
        picks.clear();

        for (UUID uuid : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                dropFromSeries(uuid, false);
                continue;
            }
            SeriesPlayerState state = playerStates.get(uuid);
            String current = currentGame.getGamePlayer(uuid).currentHeroName();

            // Lock-in: keep playing your hero while it lives.
            if (config.lockIn() && current != null && !state.isDead(current)) {
                picks.put(uuid, current);
                continue;
            }

            pendingPicks.add(uuid);
            GUIManager.open(p, new HeroSelectGUI(p, "Round " + (roundNumber + 1) + " — Pick Your Hero",
                    name -> !state.isDead(name),
                    state.deadHeroes(),
                    picked -> onPicked(uuid, picked),
                    null, null,
                    () -> leaveSeries(uuid)));
        }

        if (checkSeriesCollapse()) return;
        if (pendingPicks.isEmpty()) {
            startNextRound();
        } else {
            startPickWatchdog();
        }
    }

    private void onPicked(UUID uuid, String hero) {
        // remove()'s boolean IS the per-player "already resolved" check — a second pick from the
        // same player, or a click arriving from a ghost GUI after the phase moved on, is a no-op.
        if (phase != Phase.PICKING || !pendingPicks.remove(uuid)) return;
        // Close BEFORE anything that can teleport: player.teleport() force-closes the container
        // and fires InventoryCloseEvent synchronously, which the locked-GUI handler answers by
        // reopening this very GUI a tick later. Closing here arms GUIManager's changingGuis
        // window so that close is recognised as ours and no ghost is scheduled.
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) GUIManager.close(p);
        picks.put(uuid, hero);
        if (pendingPicks.isEmpty()) startNextRound();
    }

    /** End-early (option A): drops just this player; the series continues without them. */
    private void leaveSeries(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            GUIManager.close(p);
            p.sendMessage(Component.text("You left the series.", NamedTextColor.YELLOW));
        }
        // Leaving always works, whatever the phase; only the "everyone is ready" tail is gated.
        dropFromSeries(uuid, true);
        if (checkSeriesCollapse()) return;
        if (phase == Phase.PICKING && pendingPicks.isEmpty()) startNextRound();
    }

    private void dropFromSeries(UUID uuid, boolean teleportLobby) {
        participants.remove(uuid);
        pendingPicks.remove(uuid);
        picks.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && teleportLobby) {
            GameManager.leaveGame(p, true);
        }
    }

    /**
     * The pick phase's one-per-second heartbeat: prunes players who log out (they would stall the
     * series forever), auto-resolves stragglers once {@code series.pick-timeout} elapses, starts
     * the round the moment everyone is resolved, and otherwise tells the ready players who they
     * are waiting on.
     */
    private void startPickWatchdog() {
        cancelWatchdog();
        pickSeconds = 0;
        pickWatchdog = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            if (phase != Phase.PICKING) return;
            pickSeconds++;

            List<UUID> offline = pendingPicks.stream()
                    .filter(uuid -> Bukkit.getPlayer(uuid) == null)
                    .toList();
            if (!offline.isEmpty()) {
                offline.forEach(uuid -> dropFromSeries(uuid, false));
                if (checkSeriesCollapse()) return;
            }

            if (pickTimeout > 0 && pickSeconds >= pickTimeout) autoPickStragglers();

            if (pendingPicks.isEmpty()) {
                startNextRound();
                return;
            }
            if (pickSeconds >= waitingTitleDelay) showWaitingTitle();
        }, 20L, 20L);
    }

    /**
     * Timeout resolution: keep the straggler's current hero (or the first one still alive for
     * them). Dropping them instead would collapse a 1v1 series on a single AFK moment.
     */
    private void autoPickStragglers() {
        for (UUID uuid : new ArrayList<>(pendingPicks)) {
            SeriesPlayerState state = playerStates.get(uuid);
            String current = currentGame.getGamePlayer(uuid).currentHeroName();
            String hero = current != null && !state.isDead(current) ? current
                    : heroPool.stream().filter(name -> !state.isDead(name)).findFirst().orElse(current);
            picks.put(uuid, hero);
            pendingPicks.remove(uuid);
            Player p = Bukkit.getPlayer(uuid); // offline picks were pruned earlier this tick
            GUIManager.close(p);
            p.sendMessage(Component.text("Out of time — locked in " + HeroConfig.formatName(hero)
                    + " for this round.", NamedTextColor.RED));
        }
    }

    /**
     * "Waiting on... Alice, Bob" for everyone who has already resolved their pick. Deliberately
     * NOT sent to the players in pendingPicks: a title renders behind an open container screen,
     * so the people holding things up would only see it dimmed behind their own pick GUI.
     * Zero fade with a 2s stay, refreshed every second, keeps it steady instead of flickering.
     */
    private void showWaitingTitle() {
        Title title = Title.title(
                Component.text("Waiting on...", NamedTextColor.YELLOW),
                Component.text(formatWaiting(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO));
        for (Player p : currentGame.onlinePlayers()) {
            if (pendingPicks.contains(p.getUniqueId())) continue;
            p.showTitle(title);
        }
    }

    /** "Alice, Bob" — or "Alice, Bob, Carol +2 more" past three, to fit one subtitle line. */
    private String formatWaiting() {
        List<String> names = pendingPicks.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .sorted()
                .toList();
        if (names.size() <= 3) return String.join(", ", names);
        return String.join(", ", names.subList(0, 3)) + " +" + (names.size() - 3) + " more";
    }

    private void cancelWatchdog() {
        if (pickWatchdog != null) {
            pickWatchdog.cancel();
            pickWatchdog = null;
        }
    }

    // === Round transitions ===

    private void startNextRound() {
        // Defence in depth behind the phase gate in onPicked: no matter what re-enters here — a
        // ghost GUI click, a watchdog tick racing the last pick — only one transition ever runs.
        if (phase == Phase.TRANSITIONING || seriesOver) return;
        phase = Phase.TRANSITIONING;
        cancelWatchdog();
        roundNumber++;

        Game oldGame = currentGame;
        // Fresh map every round from the preset's map pool (gamemodes.yml map-pool key);
        // one retry avoids an immediate repeat when the pool allows.
        MapPool pool = GameModeConfigManager.getMapPool(preset);
        GameMap nextMap = GameMapManager.getRandomMapForPool(pool);
        if (currentMap != null && nextMap.name().equals(currentMap.name())) {
            nextMap = GameMapManager.getRandomMapForPool(pool);
        }
        currentMap = nextMap;
        Game newGame = new Game(nextMap, gameMode);
        newGame.settings().copy(oldGame.settings());
        newGame.defaultTeamSettings().copy(oldGame.defaultTeamSettings());
        newGame.setGameModePreset(preset);
        newGame.setModeConfig(config);
        newGame.setRoundEndCallback(this::onRoundEnd);
        newGame.setSeriesInfo(seriesId, roundNumber);
        currentGame = newGame;

        // Nobody keeps a GUI across the transition. Every path below teleports, and a container
        // closed by a teleport is exactly what resurrects a locked GUI one tick later (the same
        // rule endSeries already applies).
        for (Player p : oldGame.onlinePlayers()) {
            GUIManager.close(p);
        }

        // Non-participants still in the old world (stragglers) go to the lobby — except
        // eliminated series spectators, who ride along into the next round.
        for (Player p : oldGame.onlinePlayers()) {
            if (!participants.contains(p.getUniqueId())
                    && !eliminatedSpectators.contains(p.getUniqueId())) {
                GameManager.leaveGame(p, true);
            }
        }

        // Transfer participants old world -> new world with their series team + pick.
        for (UUID uuid : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                dropFromSeries(uuid, false);
                continue;
            }
            GamePlayer oldState = oldGame.getGamePlayer(uuid);
            oldGame.silentRemove(p);
            newGame.addPlayer(p, seriesTeams.get(uuid));
            GameManager.registerEntity(uuid, newGame);
            GamePlayer newState = newGame.getGamePlayer(uuid);
            newState.settings().copy(oldState.settings());
            if (config.stocks() > 0) newState.settings().setLives(config.stocks());
            newState.setHeroOverride(picks.get(uuid));
        }

        // Eliminated players spectate the new round (offline ones drop off the ride).
        for (UUID uuid : new ArrayList<>(eliminatedSpectators)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                eliminatedSpectators.remove(uuid);
                continue;
            }
            oldGame.silentRemove(p);
            newGame.addPlayer(p, TeamColor.SPECTATOR);
            GameManager.registerEntity(uuid, newGame);
        }
        oldGame.cleanupWorld(); // everyone is out — delete the old round's world

        if (checkSeriesCollapse()) return;

        newGame.start();
        phase = Phase.ROUND; // only after start(), so a same-tick re-entry can't slip through
        broadcast(Component.text("Round " + roundNumber + "!", NamedTextColor.AQUA));
        scheduleHpRestore(newGame);
    }

    /**
     * HP carry-over restore (A6.4): after the countdown's applyEffects pass, set each
     * player back to their hero's stored HP (clamped to max). Snapshots hold actual
     * bukkit health, so a 0-displayed player restores to the 0.5 buffer — still alive.
     */
    private void scheduleHpRestore(Game game) {
        if (!config.hpCarryover()) return;
        long delay = 20L * GameConfigManager.getCountdownTime() + 5L;
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            if (seriesOver || game != currentGame || game.gameState() != GameState.IN_PROGRESS) return;
            for (UUID uuid : participants) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                GamePlayer gamePlayer = game.getGamePlayer(uuid);
                Double stored = playerStates.get(uuid).storedHp(gamePlayer.currentHeroName());
                if (stored == null || stored < 0) continue;
                double max = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
                double hp = Math.min(stored, max);
                p.setHealth(hp);
                game.getVisuals().updateHealthScore(p, (int) hp);
            }
        }, delay);
    }

    // === Series end ===

    /** True (and ends the series) when zero teams or one team remain among participants. */
    private boolean checkSeriesCollapse() {
        if (seriesOver) return true;
        if (participants.isEmpty()) {
            endSeries(null);
            return true;
        }
        Set<TeamColor> alive = new HashSet<>();
        for (UUID uuid : participants) alive.add(seriesTeams.get(uuid));
        if (alive.size() == 1) {
            endSeries(alive.iterator().next());
            return true;
        }
        return false;
    }

    private void endSeries(TeamColor winnerColor) {
        if (seriesOver) return;
        seriesOver = true;
        phase = Phase.OVER;
        cancelWatchdog();

        Game finalGame = currentGame;
        for (Player p : finalGame.onlinePlayers()) {
            GUIManager.close(p); // a locked pick GUI would otherwise reopen forever
        }

        // Announce a beat after the final round's own win screen so both are seen.
        Component msg = winnerColor != null
                ? Component.text(winnerColor.displayName() + " wins the series!", NamedTextColor.GOLD)
                : Component.text("The series ended in a draw.", NamedTextColor.GRAY);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            for (Player p : finalGame.onlinePlayers()) {
                p.sendMessage(msg);
                p.showTitle(Title.title(msg, Component.empty()));
            }
        }, 60L);

        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), finalGame::clean,
                20L * GameConfigManager.getCleanupDelay());
    }

    private void broadcast(Component message) {
        for (Player p : currentGame.onlinePlayers()) {
            p.sendMessage(message);
        }
    }

    /** "Series score — Red: 2, Blue: 1 (first to 2)" from the plain-SERIES tally. */
    private Component seriesScore() {
        Set<TeamColor> teams = new LinkedHashSet<>();
        for (UUID uuid : participants) teams.add(seriesTeams.get(uuid));
        StringBuilder score = new StringBuilder("Series score — ");
        boolean first = true;
        for (TeamColor color : teams) {
            if (!first) score.append(", ");
            score.append(color.displayName()).append(": ").append(roundWins.getOrDefault(color, 0));
            first = false;
        }
        score.append(" (first to ").append(config.bestOf() / 2 + 1).append(")");
        return Component.text(score.toString(), NamedTextColor.AQUA);
    }
}
