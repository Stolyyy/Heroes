package me.stolyy.heroes.Games;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {
    private final Heroes plugin;
    private final HeroManager heroManager;
    private final Map<String, Game> games;
    private final Map<UUID, String> playerGames;

    public GameManager(Heroes plugin, HeroManager heroManager) {
        this.plugin = plugin;
        this.heroManager = heroManager;
        this.games = new HashMap<>();
        this.playerGames = new HashMap<>();
    }

    public Game createGame(GameEnums.GameMode gameMode, List<Player> players) {
        String gameId = UUID.randomUUID().toString();
        Game game = new Game(plugin, heroManager, gameId, gameMode, players);
        games.put(gameId, game);

        for (Player player : players) {
            game.addPlayer(player); // This should add the player with no team assigned initially
            playerGames.put(player.getUniqueId(), gameId);
            player.sendMessage("You have joined a " + gameMode.name() + " game.");
        }

        // If it's a party game, open the GUI for the party leader
        if (gameMode == GameEnums.GameMode.PARTY && !players.isEmpty()) {
            game.getGameGUI().openInventory(players.get(0));
        }

        return game;
    }

    // This overload can be used for creating games with a single player (e.g., for 1v1 mode)
    public Game createGame(GameEnums.GameMode gameMode) {
        return createGame(gameMode, new ArrayList<>());
    }

    public boolean joinGame(Player player, GameEnums.GameMode gameMode) {
        // Check if player is already in a game
        if (isPlayerInGame(player)) {
            player.sendMessage("You are already in a game. Leave your current game first.");
            return false;
        }

        PartyManager partyManager = plugin.getPartyManager();
        UUID partyLeaderUUID = partyManager.getPartyLeader(player.getUniqueId());

        // Check party requirements
        if (!checkPartyRequirements(player, gameMode)) {
            return false;
        }

        // For 2v2 and party modes, only the party leader can join
        if ((gameMode == GameEnums.GameMode.TWO_V_TWO || gameMode == GameEnums.GameMode.PARTY)
                && !partyManager.isPartyLeader(player.getUniqueId())) {
            player.sendMessage("Only the party leader can join a game.");
            return false;
        }

        Game availableGame = findAvailableGame(gameMode);
        if (availableGame == null) {
            List<Player> playersToAdd = new ArrayList<>();
            if (gameMode == GameEnums.GameMode.ONE_V_ONE) {
                playersToAdd.add(player);
            } else {
                Set<UUID> partyMembers = partyManager.getPartyMembers(partyLeaderUUID);
                for (UUID memberUUID : partyMembers) {
                    Player member = Bukkit.getPlayer(memberUUID);
                    if (member != null) {
                        playersToAdd.add(member);
                    }
                }
            }
            availableGame = createGame(gameMode, playersToAdd);
        } else if (gameMode == GameEnums.GameMode.ONE_V_ONE) {
            if (!availableGame.addPlayer(player)) {
                return false;
            }
        } else {
            Set<UUID> partyMembers = partyManager.getPartyMembers(partyLeaderUUID);
            for (UUID memberUUID : partyMembers) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null) {
                    if (!availableGame.addPlayer(member)) {
                        // If we can't add a party member, remove all added members and return false
                        for (Player addedMember : availableGame.getPlayers().keySet()) {
                            if (partyMembers.contains(addedMember.getUniqueId())) {
                                availableGame.removePlayer(addedMember);
                                playerGames.remove(addedMember.getUniqueId());
                            }
                        }
                        player.sendMessage("Unable to add all party members to the game.");
                        return false;
                    }
                    playerGames.put(memberUUID, availableGame.getGameId());
                }
            }
        }


        // Open the game GUI for the party leader
        if (gameMode == GameEnums.GameMode.PARTY && partyManager.isPartyLeader(player.getUniqueId())) {
            availableGame.getGameGUI().openInventory(player);
        }

        // If the game is now full, start it
        if (isGameFull(availableGame)) {
            availableGame.startGame();
        }

        return true;
    }

    private boolean checkPartyRequirements(Player player, GameEnums.GameMode gameMode) {
        PartyManager partyManager = plugin.getPartyManager();
        int partySize = partyManager.getPartySize(player.getUniqueId());

        switch (gameMode) {
            case ONE_V_ONE:
                if (partySize > 1) {
                    player.sendMessage("You can't join 1v1 mode while in a party.");
                    return false;
                }
                break;
            case TWO_V_TWO:
                if (partySize != 2) {
                    player.sendMessage("You need to be in a party of 2 to join 2v2 mode.");
                    return false;
                }
                break;
            case PARTY:
                if (partySize < 2 || partySize > 18) {
                    player.sendMessage("Party mode requires a party of 2 to 18 players.");
                    return false;
                }
                break;
        }
        return true;
    }

    private Game findAvailableGame(GameEnums.GameMode gameMode) {
        for (Game game : games.values()) {
            if (game.getGameMode() == gameMode && game.getGameState() == GameEnums.GameState.WAITING && !isGameFull(game)) {
                return game;
            }
        }
        return null;
    }

    private boolean isGameFull(Game game) {
        int playerCount = game.getPlayers().size();
        switch (game.getGameMode()) {
            case ONE_V_ONE:
                return playerCount >= 2;
            case TWO_V_TWO:
                return playerCount >= 4;
            case PARTY:
                return playerCount >= 18;
            default:
                return false;
        }
    }

    public void leaveGame(Player player) {
        String gameId = playerGames.remove(player.getUniqueId());
        if (gameId != null) {
            Game game = games.get(gameId);
            if (game != null) {
                game.removePlayer(player);
                if (game.getPlayers().isEmpty()) {
                    games.remove(gameId);
                }
            }
            plugin.teleportToSpawn(player);
        }
    }

    public Game getPlayerGame(Player player) {
        String gameId = playerGames.get(player.getUniqueId());
        return gameId != null ? games.get(gameId) : null;
    }

    public boolean isPlayerInGame(Player player) {
        return playerGames.containsKey(player.getUniqueId());
    }

    public void endGame(String gameId) {
        Game game = games.remove(gameId);
        if (game != null) {
            for (Player player : game.getPlayers().keySet()) {
                playerGames.remove(player.getUniqueId());
                plugin.teleportToSpawn(player);
            }
            for (Player spectator : game.getSpectators()) {
                plugin.teleportToSpawn(spectator);
            }
        }
    }

    public void startGame(String gameId) {
        Game game = games.get(gameId);
        if (game != null && game.getGameState() == GameEnums.GameState.WAITING) {
            game.startGame();
        }
    }

    public boolean addSpectator(Player player, String gameId) {
        Game game = games.get(gameId);
        if (game != null && game.getGameState() == GameEnums.GameState.IN_PROGRESS) {
            if (game.addSpectator(player)) {
                playerGames.put(player.getUniqueId(), gameId);
                return true;
            }
        }
        return false;
    }

    public void removeSpectator(Player player) {
        String gameId = playerGames.remove(player.getUniqueId());
        if (gameId != null) {
            Game game = games.get(gameId);
            if (game != null) {
                game.getSpectators().remove(player);
            }
            plugin.teleportToSpawn(player);
        }
    }

    public void handlePlayerDeath(Player player) {
        Game game = getPlayerGame(player);
        if (game != null) {
            game.playerDied(player);
        }
    }

    public void broadcastToGame(String gameId, String message) {
        Game game = games.get(gameId);
        if (game != null) {
            game.broadcastMessage(message);
        }
    }

    public void updateGameSettings(String gameId, boolean friendlyFire, boolean randomHeroes, boolean ultimateAbilities) {
        Game game = games.get(gameId);
        if (game != null) {
            game.setFriendlyFire(friendlyFire);
            game.setRandomHeroes(randomHeroes);
            game.setUltimateAbilities(ultimateAbilities);
        }
    }

    public Map<String, Game> getActiveGames() {
        return new HashMap<>(games);
    }

    public void cleanupGames() {
        games.entrySet().removeIf(entry -> {
            Game game = entry.getValue();
            if (game.getPlayers().isEmpty() && game.getGameState() != GameEnums.GameState.IN_PROGRESS) {
                for (Player spectator : game.getSpectators()) {
                    removeSpectator(spectator);
                }
                return true;
            }
            return false;
        });
    }
}