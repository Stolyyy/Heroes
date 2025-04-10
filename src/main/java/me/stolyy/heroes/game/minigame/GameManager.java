package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.game.menus.PartyGUI;
import me.stolyy.heroes.party.PartyManager;
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

    public static void join(Player player, GameEnums.GameMode gameMode){

        //check if player is in a game already
        //if they are, make them leave their game to join this one
        //if in a party, do the same to the members of their party
        if(getPlayerGame(player) != null){
            leaveGame(player);
        }

        Set<Game> games = filterByMode(waitingGames, gameMode);
        Game g;
        switch(gameMode){
            case PARTY:
                if(!PartyManager.isInParty(player)){
                    player.sendMessage("You must be in a party to join party mode!");
                }
                g = createNewGame(gameMode);
                for(Player p : PartyManager.getPlayersInParty(player)){
                    g.addPlayer(p);
                }
                new PartyGUI(g, player);
                updateGameStatus(g);
                //Create a new game
                //Add all players in the party
                //open GUI for leader
                break;
            case ONE_V_ONE:
                //only allow joining if not in party or in party of 1
                //Find game
                //if no game, create game
                //if game is full, start it
                if(PartyManager.isInParty(player)) {
                    player.sendMessage("You cannot enter 1v1 in a party, please join the Party gamemode for custom games");
                    break;
                }

                if(!games.isEmpty()){
                    g = games.iterator().next();
                    g.addPlayer(player);
                }

                else {
                    g = createNewGame(gameMode);
                    games.add(g);
                    g.addPlayer(player);
                    if (g.canCountdown()) g.countdown();
                }
                updateGameStatus(g);
                break;
            case TWO_V_TWO:
                //only allow joining in party of 2
                //find game
                //if no game, make new game
                //add both members of party
                //start game if it's full now
                if(!PartyManager.isInParty(player) || PartyManager.getPartySize(player) != 2) {
                    player.sendMessage("You must be in a party of size 2 to join 2v2");
                }

                if(!waitingGames.isEmpty()){
                    g = waitingGames.iterator().next();
                    g.addPlayer(player);
                }

                else {
                    g = createNewGame(gameMode);
                    g.addPlayer(player);
                }

                for(Player p : PartyManager.getPlayersInParty(player)){
                    g.addPlayer(p);
                }

                if(g.canCountdown() && g.getPlayerList().size() == 4) g.countdown();
                updateGameStatus(g);
                break;
        }
    }

    public static boolean leaveGame(Player player){
        Game game = getPlayerGame(player);
        if(game == null) return false;
        game.removePlayer(player);
        updateGameStatus(game);
        return true;
    }

    private static Set<Game> filterByMode(Set<Game> gameSet, GameEnums.GameMode gameMode){
        Set<Game> filtered = new HashSet<>();
        for(Game g : gameSet){
            if(g.getGameMode() == gameMode) filtered.add(g);
        }
        return filtered;
    }

    static void updateGameStatus(Game game){
        if(game == null) return;

        switch(game.getGameState()){
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

    static void setPlayerGame(Player player, Game game){
        playerGames.put(player, game);
    }

    public static Set<Game> getActiveGames(){
        return activeGames;
    }

    public static Set<Game> getWaitingGames(){
        return waitingGames;
    }
}
