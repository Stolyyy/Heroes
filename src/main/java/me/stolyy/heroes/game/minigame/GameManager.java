package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.party.PartyManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GameManager {
    private static final Set<Game> waitingGames = new HashSet<>();
    private static final Set<Game> activeGames = new HashSet<>();
    private static final Map<Player, Game> playerGames = new HashMap<>();



    public static Game createNewGame(GameEnums.GameMode gameMode){
        return new Game(GameMapManager.getRandomMap(), gameMode);
    }

    public static Game createNewGame(GameEnums.GameMode gameMode, GameMap map){
        return new Game(map, gameMode);
    }

    public static void joinGame(Player player, GameEnums.GameMode gameMode) {
        if (getPlayerGame(player) != null) {
            leaveGame(player);
        }

        switch (gameMode) {
            case ONE_V_ONE -> {
                if (PartyManager.isInParty(player)) {
                    player.sendMessage(NamedTextColor.RED + "You cannot enter 1v1 in a party. Try Party mode instead.");
                    return;
                }

                Set<Game> games = filterByMode(waitingGames, gameMode);
                Game game;
                if (!games.isEmpty())
                    game = games.iterator().next();
                else game = createNewGame(gameMode);
                game.addPlayer(player);

                if (game.canCountdown()) game.countdown();
                playerGames.put(player, game);
                updateGameStatus(game);

            }
            case TWO_V_TWO -> {
                if (!PartyManager.isInParty(player) || PartyManager.getPartySize(player) != 2) {
                    player.sendMessage(NamedTextColor.RED + "You must be in a party of 2 players to joinGame 2v2");
                    return;
                }

                Set<Game> games = filterByMode(waitingGames, gameMode);
                Game game;
                if (!games.isEmpty())
                    game = games.iterator().next();
                else game = createNewGame(gameMode);
                game.addPlayer(player);

                for (Player p : game.allPlayers()) {
                    playerGames.put(p, game);
                }

                if (game.canCountdown()) game.countdown();
                updateGameStatus(game);

            }
            case PARTY -> {
                if (!PartyManager.isInParty(player)) {
                    player.sendMessage(NamedTextColor.RED + "You must be in a party to joinGame party mode!");
                    return;
                }
                Game game = createNewGame(gameMode);
                game.addPlayer(player);
                for (Player p : game.allPlayers()) {
                    playerGames.put(p, game);
                }

                updateGameStatus(game);
            }
        }
    }

    public static boolean leaveGame(Player player){
        Game game = getPlayerGame(player);
        if(game == null) return false;

        //remove teammate & party members if someone leaves while waiting
        if((game.gameMode() == GameEnums.GameMode.TWO_V_TWO || game.gameMode() == GameEnums.GameMode.PARTY) && game.gameState() == GameEnums.GameState.WAITING) {
            for(Player p : PartyManager.getPlayersInParty(player)) {
                game.removePlayer(p);
                playerGames.remove(p);
            }
        }
        else {
            game.removePlayer(player);
            playerGames.remove(player);
        }
        updateGameStatus(game);
        return true;
    }

    private static Set<Game> filterByMode(Set<Game> gameSet, GameEnums.GameMode gameMode){
        Set<Game> filtered = new HashSet<>();
        for(Game g : gameSet){
            if(g.gameMode() == gameMode) filtered.add(g);
        }
        return filtered;
    }

    static void updateGameStatus(Game game){
        if(game == null) return;

        switch(game.gameState()){
            case WAITING -> {
                activeGames.remove(game);
                waitingGames.add(game);
            } case IN_PROGRESS, STARTING -> {
                waitingGames.remove(game);
                activeGames.add(game);
            } default -> {
                activeGames.remove(game);
                waitingGames.remove(game);
            }
        }
    }

    public static Game getPlayerGame(Player player){
        return playerGames.get(player);
    }

    public static boolean isPlayerInGame(Player player){
        return playerGames.get(player) != null;
    }

    public static Set<Game> getActiveGames(){
        return activeGames;
    }

    public static Set<Game> getWaitingGames(){
        return waitingGames;
    }

    public static void removePlayerGame(Player player){
        playerGames.remove(player);
    }

    public static void setPlayerGame(Player player, Game game){
        if (getPlayerGame(player) != null) {
            leaveGame(player);
        }
        playerGames.put(player, game);
    }
}
