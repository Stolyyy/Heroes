package me.stolyy.heroes.Games;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.DisplaySlot;

import java.util.*;

public class Game {
    private final Heroes plugin;
    private final HeroManager heroManager;
    private final String gameId;
    private final GameEnums.GameMode gameMode;
    private GameEnums.GameState gameState;
    public final Map<Player, GameEnums.GameTeam> players;
    private final List<Player> spectators;
    private final Map<GameEnums.GameTeam, Location> spawnPoints;
    private boolean friendlyFire;
    private boolean randomHeroes;
    private boolean ultimateAbilities;
    private Scoreboard scoreboard;
    private final Map<Player, Integer> lives;
    private final int maxLives = 3;
    private GameGUI gameGUI;
    private Objective sidebarObjective;
    private Objective healthObjective;
    private Set<UUID> restrictedPlayers = new HashSet<>();
    private Set<UUID> playersInGUI = new HashSet<>();


    public Game(Heroes plugin, HeroManager heroManager, String gameId, GameEnums.GameMode gameMode, List<Player> players) {
        this.plugin = plugin;
        this.heroManager = heroManager;
        this.gameId = gameId;
        this.gameMode = gameMode;
        this.gameState = GameEnums.GameState.WAITING;
        this.players = new HashMap<>();
        this.spectators = new ArrayList<>();
        this.spawnPoints = new HashMap<>();
        this.friendlyFire = false;
        this.randomHeroes = false;
        this.ultimateAbilities = true;
        this.lives = new HashMap<>();
        initializeSpawnPoints();
        initializeScoreboard();
        this.gameGUI = new GameGUI(plugin, this);
        if (gameMode == GameEnums.GameMode.TWO_V_TWO || gameMode == GameEnums.GameMode.PARTY) {
            for (Player player : players) {
                addPlayer(player);
            }
        }
    }

    private void initializeScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        // Create teams
        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            Team scoreboardTeam = scoreboard.registerNewTeam(team.name());
            scoreboardTeam.setColor(team.getChatColor());
            scoreboardTeam.setAllowFriendlyFire(friendlyFire);
        }

        // Create sidebar objective
        sidebarObjective = scoreboard.registerNewObjective("gameInfo", "dummy", ChatColor.GOLD + "Game Info");
        sidebarObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        healthObjective = scoreboard.registerNewObjective("health", "dummy", "❤");
        healthObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        updateScoreboard();
    }

    public void updateScoreboard() {
        // Clear existing scores
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // Add header
        sidebarObjective.getScore(ChatColor.YELLOW + "Players:").setScore(players.size() + 1);

        // Add players and their life counts
        int score = players.size();
        for (Map.Entry<Player, GameEnums.GameTeam> entry : players.entrySet()) {
            Player player = entry.getKey();
            GameEnums.GameTeam team = entry.getValue();
            int lives = this.lives.getOrDefault(player, maxLives);

            if (team != null) {
                String playerInfo = team.getChatColor() + player.getName() + ChatColor.WHITE + ": " + lives + " lives";
                sidebarObjective.getScore(playerInfo).setScore(score);
                score--;
            }
        }

        // Set the scoreboard for all players
        for (Player player : players.keySet()) {
            player.setScoreboard(scoreboard);
        }
    }

    private void initializeSpawnPoints() {
        spawnPoints.put(GameEnums.GameTeam.RED, new Location(Bukkit.getWorld("world"), 300, -13, 151));
        spawnPoints.put(GameEnums.GameTeam.BLUE, new Location(Bukkit.getWorld("world"), 318, -13, 151));
        spawnPoints.put(GameEnums.GameTeam.GREEN, new Location(Bukkit.getWorld("world"), 318, -13, 184));
        spawnPoints.put(GameEnums.GameTeam.YELLOW, new Location(Bukkit.getWorld("world"), 300, -13, 184));
    }

    public Map<GameEnums.GameTeam, Location> getSpawnPoints() {
        return new HashMap<>(spawnPoints);
    }

    public boolean addPlayer(Player player) {
        if (gameState != GameEnums.GameState.WAITING) {
            return false;
        }

        if (gameMode != GameEnums.GameMode.PARTY) {
            GameEnums.GameTeam team = assignTeam();
            players.put(player, team);
        } else {
            players.put(player, null);  // No team assigned for party mode initially
        }

        lives.put(player, maxLives);


        // Set max health to 100
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(100.0);
        }

        // Set health to max
        player.setHealth(100.0);

        // Make health appear as 10 hearts
        player.setHealthScale(20.0);
        player.setHealthScaled(true);

        if (gameMode == GameEnums.GameMode.PARTY) {
            teleportToWaitingArea(player);
        } else {
            teleportPlayerToSpawnPoint(player);
        }
        // Restrict player movement
        restrictPlayerMovement(player);
        updatePlayerListHealth(player);
        updateScoreboard();
        gameGUI.updateInventory();

        return true;
    }

    void teleportToWaitingArea(Player player) {
        Location waitingArea = new Location(player.getWorld(), 300, -13, 151);  // Example coordinates
        player.teleport(waitingArea);
    }

    public void playerDied(Player player) {
        int remainingLives = lives.getOrDefault(player, 0) - 1;
        lives.put(player, remainingLives);

        if (remainingLives <= 0) {
            eliminatePlayer(player);
        } else {
            respawnPlayer(player);
        }

        updateScoreboard();
        checkWinCondition();
    }

    private GameEnums.GameTeam assignTeam() {
        if (gameMode == GameEnums.GameMode.ONE_V_ONE) {
            return players.size() == 0 ? GameEnums.GameTeam.RED : GameEnums.GameTeam.BLUE;
        } else {
            // Implement logic for other game modes
            // This is a simple round-robin assignment
            return GameEnums.GameTeam.values()[players.size() % GameEnums.GameTeam.values().length];
        }
    }

    public void updatePlayerListHealth(Player player) {
        healthObjective.getScore(player.getName()).setScore((int) player.getHealth());
    }

    private void eliminatePlayer(Player player) {
        GameEnums.GameTeam team = players.remove(player);
        lives.remove(player);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.sendMessage("You have been eliminated!");
        addSpectator(player);

        // Remove player from scoreboard team
        if (team != null) {
            scoreboard.getTeam(team.name()).removeEntry(player.getName());
        }
    }

    private void respawnPlayer(Player player) {
        GameEnums.GameTeam team = players.get(player);
        player.teleport(spawnPoints.get(team));
        player.sendMessage("You have " + lives.get(player) + " lives remaining.");
    }

    private void checkWinCondition() {
        Map<GameEnums.GameTeam, Integer> teamPlayerCounts = new EnumMap<>(GameEnums.GameTeam.class);
        for (GameEnums.GameTeam team : GameEnums.GameTeam.values()) {
            teamPlayerCounts.put(team, 0);
        }

        for (Map.Entry<Player, GameEnums.GameTeam> entry : players.entrySet()) {
            GameEnums.GameTeam team = entry.getValue();
            if (team != null) {
                teamPlayerCounts.put(team, teamPlayerCounts.get(team) + 1);
            }
        }

        GameEnums.GameTeam winningTeam = null;
        int teamsWithPlayers = 0;

        for (Map.Entry<GameEnums.GameTeam, Integer> entry : teamPlayerCounts.entrySet()) {
            if (entry.getValue() > 0) {
                winningTeam = entry.getKey();
                teamsWithPlayers++;
            }
        }

        if (teamsWithPlayers == 1) {
            endGame(winningTeam);
        }
    }

    public void removePlayer(Player player) {
        players.remove(player);
        gameGUI.updateInventory();
        lives.remove(player);
        unfreezePlayer(player);

        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(20.0);
        }
        player.setHealth(20.0);
        player.setHealthScale(20.0);
        player.setHealthScaled(false);

        for (Team team : scoreboard.getTeams()) {
            team.removeEntry(player.getName());
        }
        if (players.isEmpty()) {
            endGame(null);
        }
        checkWinCondition();
    }

    public void startGame() {
        if (gameState != GameEnums.GameState.WAITING) {
            return;
        }
        gameState = GameEnums.GameState.STARTING;
        startCountdown();
        updateScoreboard();
    }

    void teleportPlayerToSpawnPoint(Player player) {
        GameEnums.GameTeam team = players.get(player);
        if (team != null && spawnPoints.containsKey(team)) {
            player.teleport(spawnPoints.get(team));
        } else {
            // If the team doesn't have a spawn point, teleport to a default location
            Location defaultSpawn = spawnPoints.values().iterator().next();
            player.teleport(defaultSpawn);
        }
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown > 0) {
                    for (Player player : players.keySet()) {
                        player.sendTitle("Game starting in", countdown + " seconds", 0, 20, 0);
                    }
                    countdown--;
                } else {
                    for (Player player : players.keySet()) {
                        player.sendTitle("Game started!", "", 0, 20, 0);
                    }
                    gameState = GameEnums.GameState.IN_PROGRESS;
                    unrestrictAllPlayers();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void unrestrictAllPlayers() {
        for (UUID uuid : restrictedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setGameMode(GameMode.SURVIVAL);
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
            }
        }
        restrictedPlayers.clear();
    }

    private void restrictPlayerMovement(Player player) {
        restrictedPlayers.add(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
    }

    private void unfreezePlayer(Player player) {
        restrictedPlayers.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
    }

    public boolean isPlayerFrozen(Player player) {
        return restrictedPlayers.contains(player.getUniqueId());
    }

    public void endGame(GameEnums.GameTeam winningTeam) {
        gameState = GameEnums.GameState.ENDED;
        String winMessage = winningTeam != null ? winningTeam.name() + " team wins!" : "Game Over!";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (players.containsKey(player) || spectators.contains(player)) {
                player.sendTitle("Game Over!", winMessage, 20, 60, 20);
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                plugin.teleportToSpawn(player);
            }
        }
        players.clear();
        spectators.clear();
        lives.clear();
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard emptyScoreboard = manager.getNewScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (players.containsKey(player) || spectators.contains(player)) {
                player.setScoreboard(emptyScoreboard);
            }
        }
    }
    public void updateAllPlayersHealth() {
        for (Player player : players.keySet()) {
            updatePlayerListHealth(player);
        }
    }


    public boolean addSpectator(Player player) {
        if (gameState != GameEnums.GameState.IN_PROGRESS) {
            return false;
        }
        spectators.add(player);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.teleport(getSpectatorLocation());
        return true;
    }

    public Location getSpectatorLocation() {
        // This method should return a suitable location for spectators
        // For example, you could return a location above the center of the map
        Location center = new Location(spawnPoints.get(GameEnums.GameTeam.RED).getWorld(), 309, -13, 167.5);
        return center.clone().add(0, 10, 0);
    }

    public GameEnums.GameState getGameState() {
        return gameState;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
        for (Team team : scoreboard.getTeams()) {
            team.setAllowFriendlyFire(friendlyFire);
        }
    }

    public boolean isRandomHeroes() {
        return randomHeroes;
    }

    public void setRandomHeroes(boolean randomHeroes) {
        this.randomHeroes = randomHeroes;
    }

    public boolean isUltimateAbilities() {
        return ultimateAbilities;
    }

    public void setUltimateAbilities(boolean ultimateAbilities) {
        this.ultimateAbilities = ultimateAbilities;
    }

    public GameEnums.GameMode getGameMode() {
        return gameMode;
    }

    public String getGameId() {
        return gameId;
    }

    public Map<Player, GameEnums.GameTeam> getPlayers() {
        return new HashMap<>(players); // Return a copy to prevent external modifications
    }

    public List<Player> getSpectators() {
        return spectators;
    }

    public GameGUI getGameGUI() {
        return gameGUI;
    }

    public int getPlayerLives(Player player) {
        return lives.getOrDefault(player, 0);
    }

    public void broadcastMessage(String message) {
        for (Player player : players.keySet()) {
            player.sendMessage(message);
        }
        for (Player spectator : spectators) {
            spectator.sendMessage(message);
        }
    }

    public boolean isPlayerInGame(Player player) {
        return players.containsKey(player) || spectators.contains(player);
    }

    public GameEnums.GameTeam getPlayerTeam(Player player) {
        return players.get(player);
    }

    public void setPlayerTeam(Player player, GameEnums.GameTeam team) {
        GameEnums.GameTeam currentTeam = players.get(player);
        if (currentTeam != null) {
            scoreboard.getTeam(currentTeam.name()).removeEntry(player.getName());
        }

        players.put(player, team);

        if (team != null) {
            scoreboard.getTeam(team.name()).addEntry(player.getName());
        }

        updateScoreboard();

        // Teleport the player to the new team's spawn point
        if (team != null) {
            teleportPlayerToSpawnPoint(player);
        } else {
            teleportToWaitingArea(player);
        }

        // Reopen the GUI for the player
        Bukkit.getScheduler().runTaskLater(plugin, () -> reopenGUIForPlayer(player), 1L);
    }

    public void reopenGUIForPlayer(Player player) {
        if (playersInGUI.contains(player.getUniqueId())) {
            gameGUI.openInventory(player);
        }
    }

    public void playerOpenedGUI(Player player) {
        playersInGUI.add(player.getUniqueId());
    }

    public void playerClosedGUI(Player player) {
        playersInGUI.remove(player.getUniqueId());
    }

    public Set<Player> getTeamPlayers(GameEnums.GameTeam team) {
        Set<Player> teamPlayers = new HashSet<>();
        for (Map.Entry<Player, GameEnums.GameTeam> entry : players.entrySet()) {
            if (entry.getValue() == team) {
                teamPlayers.add(entry.getKey());
            }
        }
        return teamPlayers;
    }

    public void resetPlayerStats(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.getInventory().clear();
        player.setExp(0);
        player.setLevel(0);
    }

    public void applyGameRules() {
        for (Player player : players.keySet()) {
            resetPlayerStats(player);
            if (randomHeroes) {
                // Implement logic to assign random heroes
            }
        }
    }
}