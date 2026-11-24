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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates a multi-round series ({@code SERIES}, {@code NUZLOCKE}, {@code IRONMAN}).
 * Adopts an arranged WAITING custom game as round 1; between rounds players pick their next hero
 * inside the finished world and transfer directly into the next round's freshly instanced world.
 * Individual {@link Game} instances track their own {@link Game.SeriesRound} metadata.
 */
public class MatchSeries {

    private final GameModePreset preset;
    private final GameModeConfig config;
    private final GameMode gameMode;
    private GameMap currentMap;
    private final List<UUID> participants = new ArrayList<>();
    private final Set<UUID> eliminatedSpectators = new LinkedHashSet<>();
    private final Map<UUID, TeamColor> seriesTeams = new HashMap<>();
    private final Map<UUID, SeriesPlayerState> playerStates = new HashMap<>();
    private final List<String> heroPool;
    private final int heroTarget;

    private Game currentGame;
    private int roundNumber = 1;
    private final String seriesId = UUID.randomUUID().toString().substring(0, 8);
    private boolean seriesOver;
    private final Map<TeamColor, Integer> roundWins = new HashMap<>();

    private final Set<UUID> pendingPicks = new HashSet<>();
    private final Map<UUID, String> picks = new HashMap<>();
    private BukkitTask pickWatchdog;
    private int pickSeconds;
    private final int pickTimeout;
    private final int waitingTitleDelay;

    private enum Phase { ROUND, PICKING, TRANSITIONING, OVER }
    private Phase phase = Phase.ROUND;

    /** Adopts the arranged WAITING custom game as round 1 and starts it immediately. */
    public static MatchSeries begin(Game setupGame) {
        return new MatchSeries(setupGame);
    }

    private MatchSeries(Game setupGame) {
        this.preset = setupGame.gameModePreset();
        this.config = setupGame.modeConfig();
        this.gameMode = setupGame.gameMode();
        this.currentMap = setupGame.settings().map();
        this.currentGame = setupGame;
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
                if (config.stocks() > 0) setupGame.getGamePlayer(uuid).settings().setLives(config.stocks());
            }
        }

        setupGame.setSeriesRound(new Game.SeriesRound(seriesId, roundNumber, this::onRoundEnd));
        setupGame.start();
        broadcast(Component.text(preset.displayName() + " series started — round 1!", NamedTextColor.AQUA));
    }

    public Game currentGame() {
        return currentGame;
    }

    private int resolveHeroTarget(String configured) {
        if (configured == null || configured.equalsIgnoreCase("all")) return heroPool.size();
        return Math.max(1, Math.min(heroPool.size(), Integer.parseInt(configured)));
    }

    private void onRoundEnd(Team winner) {
        if (seriesOver) return;
        Game round = currentGame;

        participants.removeIf(uuid -> {
            GamePlayer gamePlayer = round.getGamePlayer(uuid);
            boolean gone = gamePlayer == null || gamePlayer.leftGame() || Bukkit.getPlayer(uuid) == null;
            if (gone) {
                pendingPicks.remove(uuid);
                picks.remove(uuid);
            }
            return gone;
        });

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
                    for (UUID uuid : participants) {
                        picks.put(uuid, currentGame.getGamePlayer(uuid).currentHeroName());
                    }
                    startNextRound();
                }
            }, 20L * GameConfigManager.getCleanupDelay());
            return;
        }

        // Snapshot HP / mark dead heroes before win checks (stored HP preserves exact Bukkit health including 0.5 buffer).
        for (UUID uuid : participants) {
            GamePlayer gamePlayer = round.getGamePlayer(uuid);
            SeriesPlayerState state = playerStates.get(uuid);
            String hero = gamePlayer.currentHeroName();
            boolean survived = round.getActivePlayers().contains(uuid);
            if (preset == GameModePreset.IRONMAN) {
                if (winner != null && seriesTeams.get(uuid) == winner.color()) state.markDead(hero);
            } else if (preset == GameModePreset.NUZLOCKE) {
                if (survived) state.setHeroHp(hero, Bukkit.getPlayer(uuid).getHealth());
                else state.markDead(hero);
            } else {
                throw new IllegalStateException("Unexpected series preset in hero-tracking round end: " + preset);
            }
        }

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

        if (preset == GameModePreset.NUZLOCKE) {
            List<UUID> outOfHeroes = new ArrayList<>(participants.stream()
                    .filter(uuid -> playerStates.get(uuid).deadHeroes().size() >= heroTarget)
                    .toList());
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

        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::beginPicks,
                20L * GameConfigManager.getCleanupDelay());
    }

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

            if (config.lockIn() && current != null && !state.isDead(current)) {
                picks.put(uuid, current);
                continue;
            }

            pendingPicks.add(uuid);
            GUIManager.open(p, HeroSelectGUI.forSeries(p, roundNumber + 1,
                    name -> !state.isDead(name),
                    state.deadHeroes(),
                    picked -> onPicked(uuid, picked),
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
        if (phase != Phase.PICKING || !pendingPicks.remove(uuid)) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) GUIManager.close(p);
        picks.put(uuid, hero);
        if (pendingPicks.isEmpty()) startNextRound();
    }

    private void leaveSeries(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            GUIManager.close(p);
            p.sendMessage(Component.text("You left the series.", NamedTextColor.YELLOW));
        }
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
            GameManager.leaveToLobby(p);
        }
    }

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

    private void autoPickStragglers() {
        for (UUID uuid : new ArrayList<>(pendingPicks)) {
            SeriesPlayerState state = playerStates.get(uuid);
            String current = currentGame.getGamePlayer(uuid).currentHeroName();
            String hero = current != null && !state.isDead(current) ? current
                    : heroPool.stream().filter(name -> !state.isDead(name)).findFirst().orElse(current);
            picks.put(uuid, hero);
            pendingPicks.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            GUIManager.close(p);
            p.sendMessage(Component.text("Out of time — locked in " + HeroConfig.formatName(hero)
                    + " for this round.", NamedTextColor.RED));
        }
    }

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

    private void startNextRound() {
        if (phase == Phase.TRANSITIONING || seriesOver) return;
        phase = Phase.TRANSITIONING;
        cancelWatchdog();
        roundNumber++;

        Game oldGame = currentGame;
        MapPool pool = GameModeConfigManager.getMapPool(preset);
        GameMap nextMap = GameMapManager.getRandomMapForPool(pool);
        if (currentMap != null && nextMap.name().equals(currentMap.name())) {
            nextMap = GameMapManager.getRandomMapForPool(pool);
        }
        currentMap = nextMap;

        Game newGame = oldGame.createSuccessor(nextMap);
        newGame.setSeriesRound(new Game.SeriesRound(seriesId, roundNumber, this::onRoundEnd));
        currentGame = newGame;

        for (Player p : oldGame.onlinePlayers()) {
            GUIManager.close(p);
        }

        for (Player p : oldGame.onlinePlayers()) {
            if (!participants.contains(p.getUniqueId())
                    && !eliminatedSpectators.contains(p.getUniqueId())) {
                GameManager.leaveToLobby(p);
            }
        }

        for (UUID uuid : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                dropFromSeries(uuid, false);
                continue;
            }
            oldGame.transferPlayerTo(newGame, p, seriesTeams.get(uuid), picks.get(uuid));
            if (config.stocks() > 0) newGame.getGamePlayer(uuid).settings().setLives(config.stocks());
        }

        for (UUID uuid : new ArrayList<>(eliminatedSpectators)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                eliminatedSpectators.remove(uuid);
                continue;
            }
            oldGame.transferPlayerTo(newGame, p, TeamColor.SPECTATOR, null);
        }
        oldGame.cleanupWorld();

        if (checkSeriesCollapse()) return;

        newGame.start();
        phase = Phase.ROUND;
        broadcast(Component.text("Round " + roundNumber + "!", NamedTextColor.AQUA));
        scheduleHpRestore(newGame);
    }

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
                game.restoreHealth(p, stored);
            }
        }, delay);
    }

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
            GUIManager.close(p);
        }

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
