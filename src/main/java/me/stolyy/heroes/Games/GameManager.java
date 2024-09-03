package me.stolyy.heroes.Games;

import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.HeroManager;
import me.stolyy.heroes.Games.Party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private final HeroManager heroManager;
    private final PartyManager partyManager;
    private final MapManager mapManager;
    private final Map<String, Game> activeGames;
    private final Map<UUID, String> playerGames;

    public GameManager(HeroManager heroManager, PartyManager partyManager) {
        this.heroManager = heroManager;
        this.partyManager = partyManager;
        this.mapManager = new MapManager();
        this.activeGames = new ConcurrentHashMap<>();
        this.playerGames = new ConcurrentHashMap<>();
    }

    private boolean isValidPartySize(GameEnums.GameMode gameMode, int partySize) {
        switch (gameMode) {
            case ONE_V_ONE:
                return partySize == 1;
            case TWO_V_TWO:
                return partySize == 2;
            case PARTY:
                return partySize >= 2 && partySize <= 18;
            default:
                return false;
        }
    }

    private Game findOrCreateGame(GameEnums.GameMode gameMode) {
        // Try to find an existing game
        for (Game game : activeGames.values()) {
            if (game.getGameMode() == gameMode && game.getGameState() == GameEnums.GameState.WAITING && !isGameFull(game)) {
                return game;
            }
        }

        // Create a new game if none found
        String gameId = UUID.randomUUID().toString();
        MapData mapData = mapManager.getRandomMap();
        if (mapData == null) {
            Heroes.getInstance().getLogger().severe("Failed to get a random map. Cannot create a new game.");
            return null;
        }

        Game newGame = new Game(Heroes.getInstance(), heroManager, gameId, gameMode, mapData);
        activeGames.put(gameId, newGame);
        return newGame;
    }

    public boolean joinGame(Player player, GameEnums.GameMode gameMode) {
        if (isPlayerInGame(player)) {
            player.sendMessage("You are already in a game.");
            return false;
        }

        if (!partyManager.isPartyLeader(player.getUniqueId())) {
            player.sendMessage("Only party leaders can join games.");
            return false;
        }

        Set<UUID> partyMembers = partyManager.getPartyMembers(player.getUniqueId());
        int partySize = (partyMembers != null) ? partyMembers.size() : 1;

        if (!isValidPartySize(gameMode, partySize)) {
            player.sendMessage("Invalid party size for this game mode.");
            return false;
        }

        Game game = findOrCreateGame(gameMode);
        if (game == null) {
            player.sendMessage("Unable to find or create a game. Please try again later.");
            return false;
        }

        addPlayersToGame(game, partyMembers != null ? partyMembers : Collections.singleton(player.getUniqueId()));
        return true;
    }

    private void addPlayersToGame(Game game, Set<UUID> players) {
        List<GameEnums.GameTeam> availableTeams = new ArrayList<>(Arrays.asList(GameEnums.GameTeam.values()));
        Collections.shuffle(availableTeams);
        int teamIndex = 0;

        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                GameEnums.GameTeam team = null;
                if (game.getGameMode() != GameEnums.GameMode.PARTY) {
                    // Assign teams for non-party games
                    team = availableTeams.get(teamIndex % availableTeams.size());
                    teamIndex++;
                }

                if (game.addPlayer(player)) {
                    playerGames.put(playerId, game.getGameId());
                    if (team != null) {
                        game.setPlayerTeam(player, team);
                    }
                    player.sendMessage("You have joined a " + game.getGameMode().name() + " game.");
                } else {
                    player.sendMessage("Failed to join the game.");
                }
            }
        }

        if (game.getGameMode() == GameEnums.GameMode.PARTY) {
            openPartyGameGUI(game);
        } else if (isGameFull(game)) {
            game.startGame();
        }
    }



    private void openPartyGameGUI(Game game) {
        for (UUID playerId : game.getPlayers().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && playerId.equals(partyManager.getPartyByPlayer(playerId).getLeader())) {
                game.getGameGUI().openInventory(player);
            }
        }
    }

    private boolean isGameFull(Game game) {
        int playerCount = game.getPlayers().size();
        switch (game.getGameMode()) {
            case ONE_V_ONE:
                return playerCount >= 2;
            case TWO_V_TWO:
                return playerCount >= 4;
            case PARTY:
                return false; // Party games start manually
            default:
                return false;
        }
    }

    public void leaveGame(Player player) {
        String gameId = playerGames.remove(player.getUniqueId());
        if (gameId != null) {
            Game game = activeGames.get(gameId);
            if (game != null) {
                game.removePlayer(player);
                if (game.getPlayers().isEmpty()) {
                    endGame(gameId);
                }
            }
        }
        Heroes.getInstance().teleportToLobby(player);
    }

    public void endGame(String gameId) {
        Game game = activeGames.remove(gameId);
        if (game != null) {
            for (UUID playerId : game.getPlayers().keySet()) {
                playerGames.remove(playerId);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    Heroes.getInstance().teleportToLobby(player);
                }
            }
            for (UUID spectatorId : game.getSpectators()) {
                Player spectator = Bukkit.getPlayer(spectatorId);
                if (spectator != null) {
                    Heroes.getInstance().teleportToLobby(spectator);
                }
            }
        }
    }

    public void cancelGame(String gameId, Player canceler) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            // Check if the canceler is the party leader
            if (!partyManager.isPartyLeader(canceler.getUniqueId())) {
                canceler.sendMessage("Only the party leader can cancel the game setup.");
                return;
            }

            for (UUID playerId : new ArrayList<>(game.getPlayers().keySet())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    leaveGame(player);
                    player.sendMessage("The game setup has been canceled by the party leader.");
                }
            }
            endGame(gameId);
        }
    }

    public boolean isPlayerInGame(Player player) {
        return playerGames.containsKey(player.getUniqueId());
    }

    public Game getPlayerGame(Player player) {
        String gameId = playerGames.get(player.getUniqueId());
        return gameId != null ? activeGames.get(gameId) : null;
    }

    public void cleanupGames() {
        List<String> gamesToRemove = new ArrayList<>();
        for (Map.Entry<String, Game> entry : activeGames.entrySet()) {
            Game game = entry.getValue();
            if (game.getPlayers().isEmpty() && game.getGameState() != GameEnums.GameState.IN_PROGRESS) {
                gamesToRemove.add(entry.getKey());
            }
        }
        for (String gameId : gamesToRemove) {
            endGame(gameId);
        }
    }

    public Map<String, Game> getActiveGames() {
        return new HashMap<>(activeGames);
    }

    public List<MapData> getAllMaps() {
        return mapManager.getAllMaps();
    }

    public void clearGames() {
        for (String gameId : new ArrayList<>(activeGames.keySet())) {
            endGame(gameId);
        }
        activeGames.clear();
        playerGames.clear();
    }


    // New method to handle player movement in GUI
    public void handlePlayerMove(Player player, Player movedPlayer, GameEnums.GameTeam newTeam) {
        Game game = getPlayerGame(player);
        if (game != null && game.getGameState() == GameEnums.GameState.WAITING) {
            game.setPlayerTeam(movedPlayer, newTeam);
            game.getGameGUI().updateInventory();
        }
    }

    // New method to start a game
    public void startGame(String gameId) {
        Game game = activeGames.get(gameId);
        if (game != null && game.getGameState() == GameEnums.GameState.WAITING) {
            game.startGame();
        }
    }

    // New method to change game settings
    public void updateGameSettings(String gameId, GameSettingsManager newSettings) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            game.getSettings().updateFrom(newSettings);
        }
    }

    // New method to change game map
    public void changeGameMap(String gameId, MapData newMap) {
        Game game = activeGames.get(gameId);
        if (game != null && game.getGameState() == GameEnums.GameState.WAITING) {
            game.setMap(newMap);
        }
    }
}