package me.stolyy.heroes.Games;

import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.HeroManager;
import me.stolyy.heroes.Utility.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class Game {
    private final Heroes plugin;
    private final HeroManager heroManager;
    private final String gameId;
    private final GameEnums.GameMode gameMode;
    private GameEnums.GameState gameState;
    private final Map<UUID, GameEnums.GameTeam> players;
    private final Set<UUID> spectators;
    private final Map<GameEnums.GameTeam, List<Location>> spawnPoints;
    private final Map<UUID, Integer> lives;
    private final GameSettingsManager settings;
    private Scoreboard scoreboard;
    private Objective sidebarObjective;
    private Objective tabHealthObjective;
    private Objective belowNameHealthObjective;
    private final GameGUI gameGUI;
    private MapData currentMap;

    public Game(Heroes plugin, HeroManager heroManager, String gameId, GameEnums.GameMode gameMode, MapData initialMap) {
        this.plugin = plugin;
        this.heroManager = heroManager;
        this.gameId = gameId;
        this.gameMode = gameMode;
        this.gameState = GameEnums.GameState.WAITING;
        this.players = new HashMap<>();
        this.spectators = new HashSet<>();
        this.spawnPoints = new EnumMap<>(GameEnums.GameTeam.class);
        this.lives = new HashMap<>();
        this.settings = new GameSettingsManager();
        this.gameGUI = new GameGUI(this);
        this.currentMap = initialMap;

        initializeScoreboard();
        initializeSpawnPoints();
    }

    private void initializeScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        this.scoreboard = manager.getNewScoreboard();

        this.sidebarObjective = scoreboard.registerNewObjective("gameInfo", "dummy", ChatColor.GOLD + "Game Info");
        this.sidebarObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

        this.tabHealthObjective = scoreboard.registerNewObjective("tabHealth", "health", "❤");
        this.tabHealthObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        // Initialize the health objective for below player names
        this.belowNameHealthObjective = scoreboard.registerNewObjective("belowNameHealth", "health", "❤");
        this.belowNameHealthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);

        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            Team scoreboardTeam = scoreboard.registerNewTeam(team.name());
            scoreboardTeam.setColor(team.getChatColor());
            scoreboardTeam.setAllowFriendlyFire(settings.isFriendlyFire());
        }
    }

    private void initializeSpawnPoints() {
        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            spawnPoints.put(team, new ArrayList<>());
        }
        updateSpawnPoints();
    }

    public void updateSpawnPoints() {
        Map<GameEnums.GameTeam, List<Location>> newSpawnPoints = currentMap.getTeamSpawnPoints();
        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            spawnPoints.put(team, newSpawnPoints.getOrDefault(team, new ArrayList<>()));
        }
    }

    public boolean addPlayer(Player player) {
        if (gameState != GameEnums.GameState.WAITING) {
            return false;
        }
        UUID playerUUID = player.getUniqueId();
        GameEnums.GameTeam team = assignTeam();
        players.put(playerUUID, team);
        lives.put(playerUUID, 3);

        player.setGameMode(GameMode.ADVENTURE);
        if (team != null) {
            teleportPlayerToTeamSpawn(player, team);
        } else {
            teleportPlayerToWaitingArea(player);
        }
        restrictPlayerMovement(player);

        setPlayerAttributes(player);
        Equipment.equip(player);
        updateScoreboard();
        player.setScoreboard(scoreboard);
        return true;
    }

    private GameEnums.GameTeam assignTeam() {
        Map<GameEnums.GameTeam, Integer> teamCounts = new EnumMap<>(GameEnums.GameTeam.class);
        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            teamCounts.put(team, 0);
        }
        for (GameEnums.GameTeam team : players.values()) {
            teamCounts.put(team, teamCounts.get(team) + 1);
        }
        return Collections.min(teamCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private void setPlayerAttributes(Player player) {
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(100.0);
        player.setHealth(100.0);
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0));
    }

    public void removePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        GameEnums.GameTeam team = players.remove(playerUUID);
        lives.remove(playerUUID);
        if (team != null) {
            scoreboard.getTeam(team.name()).removeEntry(player.getName());
        }
        tabHealthObjective.getScoreboard().resetScores(player.getName());
        belowNameHealthObjective.getScoreboard().resetScores(player.getName());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        resetPlayerAttributes(player);
        unrestrictPlayerMovement(player);
        updateScoreboard();
        checkWinCondition();
    }

    private void resetPlayerAttributes(Player player) {
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setHealthScale(20.0);
        player.setHealthScaled(false);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.SATURATION);
    }

    public void startGame() {
        if (gameState != GameEnums.GameState.WAITING || !canStart()) {
            return;
        }
        gameState = GameEnums.GameState.STARTING;
        startCountdown();
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown > 0) {
                    broadcastMessage("Game starting in " + countdown + " seconds!");
                    countdown--;
                } else {
                    broadcastMessage("Game started!");
                    gameState = GameEnums.GameState.IN_PROGRESS;
                    teleportPlayersToSpawns();
                    unrestrictAllPlayers();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void setPlayerTeam(Player player, GameEnums.GameTeam newTeam) {
        UUID playerUUID = player.getUniqueId();
        GameEnums.GameTeam oldTeam = players.get(playerUUID);

        if (oldTeam != null) {
            scoreboard.getTeam(oldTeam.name()).removeEntry(player.getName());
        }

        players.put(playerUUID, newTeam);

        if (newTeam != null) {
            scoreboard.getTeam(newTeam.name()).addEntry(player.getName());
            if (gameState == GameEnums.GameState.IN_PROGRESS) {
                player.teleport(currentMap.getRandomSpawnPoint(newTeam));
            }
        }

        updateScoreboard();
        player.sendMessage(ChatColor.GREEN + "You have been moved to " + (newTeam != null ? newTeam.name() : "Spectators"));
    }

    private void teleportPlayersToSpawns() {
        for (Map.Entry<UUID, GameEnums.GameTeam> entry : players.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                GameEnums.GameTeam team = entry.getValue();
                List<Location> teamSpawns = spawnPoints.get(team);
                if (teamSpawns != null && !teamSpawns.isEmpty()) {
                    Location spawn = teamSpawns.get(new Random().nextInt(teamSpawns.size()));
                    player.teleport(spawn);
                }
            }
        }
    }

    private void teleportPlayerToTeamSpawn(Player player, GameEnums.GameTeam team) {
        List<Location> teamSpawns = spawnPoints.get(team);
        if (teamSpawns != null && !teamSpawns.isEmpty()) {
            Location spawn = teamSpawns.get(new Random().nextInt(teamSpawns.size()));
            player.teleport(spawn);
        } else {
            // Fallback to waiting area if no spawn point is available
            teleportPlayerToWaitingArea(player);
        }
    }

    private void teleportPlayerToWaitingArea(Player player) {
        player.teleport(getWaitingArea());
        restrictPlayerMovement(player);
    }

    public void playerDeath(Player player) {
        UUID playerUUID = player.getUniqueId();
        int remainingLives = lives.get(playerUUID) - 1;
        lives.put(playerUUID, remainingLives);

        if (remainingLives <= 0) {
            eliminatePlayer(player);
        } else {
            respawnPlayer(player);
        }

        updateScoreboard();
        checkWinCondition();
    }

    private void eliminatePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        GameEnums.GameTeam team = players.remove(playerUUID);
        if (team != null) {
            scoreboard.getTeam(team.name()).removeEntry(player.getName());
        }
        player.setGameMode(GameMode.SPECTATOR);
        spectators.add(playerUUID);
        broadcastMessage(player.getName() + " has been eliminated!");
    }

    private void respawnPlayer(Player player) {
        setPlayerAttributes(player);
        GameEnums.GameTeam team = players.get(player.getUniqueId());
        List<Location> teamSpawns = spawnPoints.get(team);
        if (teamSpawns != null && !teamSpawns.isEmpty()) {
            Location spawn = teamSpawns.get(new Random().nextInt(teamSpawns.size()));
            player.teleport(spawn);
        }
        player.setGameMode(GameMode.ADVENTURE);
    }

    private void checkWinCondition() {
        List<GameEnums.GameTeam> remainingTeams = new ArrayList<>(new HashSet<>(players.values()));
        if (remainingTeams.size() == 1) {
            endGame(remainingTeams.get(0));
        }
    }

    public void endGame(GameEnums.GameTeam winningTeam) {
        gameState = GameEnums.GameState.ENDED;
        String winMessage = winningTeam != null ? winningTeam.name() + " team wins!" : "Game Over!";
        broadcastMessage(winMessage);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(players.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        removePlayer(player);
                        plugin.teleportToLobby(player);
                    }
                }
                for (UUID uuid : new HashSet<>(spectators)) {
                    Player spectator = Bukkit.getPlayer(uuid);
                    if (spectator != null) {
                        removeSpectator(spectator);
                        plugin.teleportToLobby(spectator);
                    }
                }
            }
        }.runTaskLater(plugin, 100); // 5 seconds delay
    }

    private void updateScoreboard() {
        // Clear all existing entries
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // Group players by team
        Map<GameEnums.GameTeam, List<Player>> teamPlayers = new EnumMap<>(GameEnums.GameTeam.class);
        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            teamPlayers.put(team, new ArrayList<>());
        }
        List<Player> spectators = new ArrayList<>();

        // Populate team players and spectators
        for (Map.Entry<UUID, GameEnums.GameTeam> entry : players.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                GameEnums.GameTeam team = entry.getValue();
                if (team != null) {
                    teamPlayers.get(team).add(player);
                } else {
                    spectators.add(player);
                }
            }
        }

        int score = teamPlayers.size() + spectators.size() + 10; // Ensure enough scores for all entries

        // Display teams and players
        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            List<Player> playersInTeam = teamPlayers.get(team);
            if (!playersInTeam.isEmpty()) {
                // Add team header
                sidebarObjective.getScore(team.getChatColor() + "== " + team.name() + " TEAM ==").setScore(score--);

                // Add players in the team
                for (Player player : playersInTeam) {
                    String playerInfo = team.getChatColor() + player.getName() + ": " + lives.get(player.getUniqueId()) + " lives";
                    sidebarObjective.getScore(playerInfo).setScore(score--);
                }

                // Add a blank line after each team (if it's not the last team)
                if (score > spectators.size() + 1) {
                    sidebarObjective.getScore(ChatColor.RESET.toString() + ChatColor.STRIKETHROUGH + "----------------------").setScore(score--);
                }
            }
        }

        // Display spectators
        if (!spectators.isEmpty()) {
            sidebarObjective.getScore(ChatColor.GRAY + "== SPECTATORS ==").setScore(score--);
            for (Player player : spectators) {
                String playerInfo = ChatColor.GRAY + player.getName();
                sidebarObjective.getScore(playerInfo).setScore(score--);
            }
        }

        // Update player scoreboards
        for (UUID uuid : players.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setScoreboard(scoreboard);
            }
        }
    }

    public boolean addSpectator(Player player) {
        if (gameState != GameEnums.GameState.IN_PROGRESS) {
            return false;
        }
        spectators.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(getWaitingArea());
        return true;
    }

    public void removeSpectator(Player player) {
        spectators.remove(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        plugin.teleportToLobby(player);
    }

    public void checkPlayerPosition(Player player) {
        if (!currentMap.getBoundingBox().contains(player.getLocation().toVector())) {
            playerDeath(player);
            player.sendMessage("You went out of bounds!");
        }
    }

    private void broadcastMessage(String message) {
        for (UUID uuid : players.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
        for (UUID uuid : spectators) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) {
                spectator.sendMessage(message);
            }
        }
    }

    private void restrictPlayerMovement(Player player) {
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
        player.setAllowFlight(false);
    }

    private void unrestrictPlayerMovement(Player player) {
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setAllowFlight(true);
    }

    private void unrestrictAllPlayers() {
        for (UUID uuid : players.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                unrestrictPlayerMovement(player);
            }
        }
    }

    public boolean isPlayerInGame(Player player) {
        return players.containsKey(player.getUniqueId()) || spectators.contains(player.getUniqueId());
    }

    public boolean isPlayerAlive(Player player) {
        return players.containsKey(player.getUniqueId());
    }

    public void setMap(MapData newMap) {
        if (newMap == null) {
            throw new IllegalArgumentException("New map cannot be null");
        }
        this.currentMap = newMap;
        updateSpawnPoints();
    }

    public Location getWaitingArea() {
        return currentMap.getWaitingArea();
    }

    public boolean canStart() {
        Set<GameEnums.GameTeam> teamsWithPlayers = new HashSet<>(players.values());
        return teamsWithPlayers.size() >= 2;
    }

    public void setPlayerAsSpectator(Player player) {
        UUID playerUUID = player.getUniqueId();
        players.put(playerUUID, null);
        updateScoreboard();
    }

    public boolean arePlayersOnSameTeam(Player player1, Player player2) {
        Game game = plugin.getGameManager().getPlayerGame(player1);

        // First, check if both players are in the same game
        if (game == null || game != plugin.getGameManager().getPlayerGame(player2)) {
            return false;
        }

        // Get the teams of both players
        GameEnums.GameTeam team1 = game.getPlayerTeam(player1);
        GameEnums.GameTeam team2 = game.getPlayerTeam(player2);

        // If both teams are null (spectators) or both teams are the same and not null, they're on the same team
        return (team1 == null && team2 == null) || (team1 != null && team1 == team2);
    }

    // Getters
    public Heroes getPlugin() { return plugin; }
    public String getGameId() { return gameId; }
    public GameEnums.GameMode getGameMode() { return gameMode; }
    public GameEnums.GameState getGameState() { return gameState; }
    public Map<UUID, GameEnums.GameTeam> getPlayers() { return new HashMap<>(players); }
    public Set<UUID> getSpectators() { return new HashSet<>(spectators); }
    public GameSettingsManager getSettings() { return settings; }
    public GameGUI getGameGUI() { return gameGUI; }
    public int getPlayerLives(Player player) { return lives.getOrDefault(player.getUniqueId(), 0); }
    public GameEnums.GameTeam getPlayerTeam(Player player) { return players.get(player.getUniqueId()); }
    public MapData getCurrentMap() { return currentMap; }

}