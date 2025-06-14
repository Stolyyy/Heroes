package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.utility.effects.Equipment;
import me.stolyy.heroes.game.minigame.GameEnums.GameMode;
import me.stolyy.heroes.game.minigame.GameEnums.TeamColor;
import me.stolyy.heroes.game.minigame.GameEnums.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.stream.Collectors;


public class Game {
    protected static final int RESPAWN_TIME = 7; //seconds
    protected static final int COUNTDOWN_TIME = 7;
    protected static final int CLEANUP_DELAY = 5;

    private final GameMode gameMode;
    private final GameSettings settings;
    private final GameVisuals visuals;
    private final Map<TeamColor, GameTeam> teams = new EnumMap<>(TeamColor.class);
    private final List<BukkitTask> activeTasks = new LinkedList<>();

    private GameState gameState;

    public Game(GameMap gameMap, GameMode gameMode) {
        this.gameMode = gameMode;

        GameMap map = GameMapManager.createWorld(gameMap);
        settings = new GameSettings(Objects.requireNonNull(map));
        visuals = new GameVisuals(this);
        gameState = GameState.WAITING;

        for (GameEnums.TeamColor color : GameEnums.TeamColor.values()) {
            teams.put(color, new GameTeam(color));
        }
    }



    //GAMESTATE METHODS
    public void start() {
        if (gameState != GameState.WAITING) return;

        setGameState(GameState.STARTING);
        visuals.showCountdown();

        for(GameTeam team : teams.values()) team.initialize();

        for(Player p : onlinePlayers(false)) {
            HeroManager.getHero(p).onCountdown();
            GameEffects.restrictPlayer(p);
            GameEffects.applyEffects(p, playerTeam(p).settings());
            Equipment.equip(p);
        }


        BukkitTask startTask = Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            setGameState(GameState.IN_PROGRESS);
            visuals.startTimer();
            for (Player p : onlinePlayers(false)) { // Get non-spectators
                HeroManager.getHero(p).onGameStart();
                GameEffects.unRestrictPlayer(p);
            }
        }, 20 * COUNTDOWN_TIME);
        activeTasks.add(startTask);
    }

    public void end(GameEnums.GameEndReason reason) {
        if (gameState == GameState.ENDED) return;

        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        visuals.cancelTasks();

        if(reason == GameEnums.GameEndReason.TIMEOUT){
            calculateWinnerByLives();
        } else {
            Optional<GameTeam> winningTeam = teams.values().stream()
                    .filter(t -> t.color() != GameEnums.TeamColor.SPECTATOR && !t.onlinePlayers().isEmpty())
                    .findFirst();
            winningTeam.ifPresentOrElse(visuals::win, visuals::draw);
        }

        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::clean, 20L * CLEANUP_DELAY);
    }



    //PLAYER METHODS
    public void addPlayer(Player player, TeamColor teamColor) {
        if(gameState != GameState.WAITING) teamColor = TeamColor.SPECTATOR;

        GameTeam team = teams.get(teamColor);
        team.add(player);

        if(teamColor == TeamColor.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        } else {
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            GameEffects.restrictPlayer(player);
        }

        teleportToSpawn(player, teamColor);
        visuals.update();
    }

    public void removePlayer(Player player) {
        GameTeam team = playerTeam(player);
        if(team == null) return;

        team.remove(player);
        HeroManager.getHero(player).onElimination();
        GameEffects.removeEffects(player);
        GameEffects.unRestrictPlayer(player);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        visuals.update();
        checkEnd();
    }

    public void changeTeam(Player player, TeamColor teamColor) {
        GameTeam oldTeam = playerTeam(player);
        if(oldTeam == null || oldTeam.color() == teamColor) return;
        else oldTeam.remove(player);

        teams.get(teamColor).add(player);

        if (teamColor == TeamColor.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            GameEffects.unRestrictPlayer(player);
        } else {
            if (gameState == GameState.WAITING) GameEffects.restrictPlayer(player);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            teleportToSpawn(player, teamColor);
        }

        visuals.update();
    }



    //GAME LOGIC
    protected void handleDeath(Player player) {
        if (gameState != GameState.IN_PROGRESS) return;

        GameTeam team = playerTeam(player);
        if (team == null) return;
        team.subtractLife(player);
        HeroManager.getHero(player).onDeath();

        if(team.lives(player) <= 0) {
            player.sendMessage("You have been eliminated!");
            changeTeam(player, GameEnums.TeamColor.SPECTATOR);
            HeroManager.getHero(player).onElimination();
            checkEnd();
        } else {
            visuals.respawning(player);
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            GameEffects.restrictPlayer(player);
            BukkitTask respawnTask = Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> handleRespawn(player), 20 * RESPAWN_TIME);
            activeTasks.add(respawnTask);
        }
        visuals.update();
    }

    private void handleRespawn(Player player) {
        if(gameState != GameState.IN_PROGRESS) return;

        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        GameEffects.unRestrictPlayer(player);
        GameEffects.applyEffects(player, playerTeam(player).settings());
        HeroManager.getHero(player).onRespawn();
    }

    public boolean canStart() {
        long teamsWithPlayers = teams.values().stream()
                .filter(t -> t.color() != GameEnums.TeamColor.SPECTATOR)
                .filter(t -> !t.playerUUIDs().isEmpty())
                .count();
        return gameState == GameState.WAITING && teamsWithPlayers >= 2;
    }

    public boolean checkEnd() {
        if(gameState == GameState.ENDED) return true;

        long teamsWithPlayers = teams.values().stream()
                .filter(t -> t.color() != GameEnums.TeamColor.SPECTATOR)
                .filter(t -> !t.playerUUIDs().isEmpty())
                .count();
        if(teamsWithPlayers <= 1) {
            end(GameEnums.GameEndReason.LAST_TEAM_STANDING);
            return true;
        }
        return false;
    }

    private void calculateWinnerByLives() {
        GameTeam winningTeam = null;
        int maxLives = -1;
        boolean isDraw = false;

        for (GameTeam team : teams.values()) {
            if (team.color() == GameEnums.TeamColor.SPECTATOR) continue;

            int totalLives = team.totalLives();
            if (totalLives > maxLives) {
                maxLives = totalLives;
                winningTeam = team;
                isDraw = false;
            } else if (totalLives == maxLives) {
                isDraw = true;
            }
        }

        if (isDraw || winningTeam == null) {
            visuals.draw();
        } else {
            visuals.win(winningTeam);
        }
    }

    public void clean() {
        for (UUID uuid : getAllPlayerUUIDs()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                HeroManager.getHero(p).cancelTasks();
                Heroes.teleportToLobby(p);
            }
        }
        if (settings.map() != null) {
            GameMapManager.deleteWorld(settings.map());
        }
    }

    private void teleportToSpawn(Player player, TeamColor teamColor) {
        if (teamColor == null || teamColor == GameEnums.TeamColor.SPECTATOR) {
            player.teleport(settings.map().spectatorLocation());
            return;
        }

        Location[] spawns = settings.map().spawnLocations();
        int spawnIndex = teamColor.ordinal() % spawns.length;
        Location teleportLocation = spawns[spawnIndex].clone();

        int teamSize = playerTeam(player).onlinePlayers().size();
        if(teamSize == 1) player.teleport(teleportLocation);
        else if(teamSize == 2) player.teleport(teleportLocation.add(1,0,0));
        else player.teleport(teleportLocation.add(-1,0,0));
    }


    //GETTERS
    public boolean sameTeam(Player first, Player second){ return playerTeam(first) == playerTeam(second); }
    public boolean friendlyFire(Player player){ return playerTeam(player).settings().friendlyFire(); }
    public boolean ultimateEnabled(Player player) { return playerTeam(player).settings().ultimatesEnabled();}
    public int lives(Player player){ return playerTeam(player).lives(player);}
    public TeamColor playerColor(Player player) { return playerTeam(player).color(); }

    public GameState gameState() { return gameState; }
    public GameMode gameMode() { return gameMode; }
    public GameSettings settings() { return settings; }
    public Map<GameEnums.TeamColor, GameTeam> getTeams() { return Collections.unmodifiableMap(teams); }

    public Set<Player> onlinePlayers(){ return onlinePlayers(true); }

    public Set<Player> onlinePlayers(boolean includeSpectators) {
        return teams.values().stream()
                .filter(team -> includeSpectators || team.color() != GameEnums.TeamColor.SPECTATOR)
                .flatMap(team -> team.onlinePlayers().stream())
                .collect(Collectors.toSet());
    }

    private Set<UUID> getAllPlayerUUIDs() {
        return teams.values().stream()
                .flatMap(team -> team.playerUUIDs().stream())
                .collect(Collectors.toSet());
    }

    public GameTeam playerTeam(Player player) {
        for (GameTeam team : teams.values()) {
            if (team.contains(player)) return team;
        }
        return null;
    }

    public Location getRespawnLocation(Player player){
        if(playerTeam(player) == null || playerTeam(player).color() == TeamColor.SPECTATOR) {
            return settings.map().spectatorLocation();
        }
        return settings.map().getFurthestSpawn(player, onlinePlayers(false).stream()
                .filter(p -> !sameTeam(player, p))
                .collect(Collectors.toSet()));
    }

    public BoundingBox mapBounds(){
        return settings.boundaries();
    }

    private void setGameState(GameState newState) {
        this.gameState = newState;
    }

    public void setAllTeamSettings(TeamSettings teamSettings){
        for(GameTeam team : teams.values()){
            if(team.color() == TeamColor.SPECTATOR) continue;
            team.setSettings(teamSettings);
        }
    }
}