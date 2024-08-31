package me.stolyy.heroes.Game;

import org.bukkit.entity.Player;

import java.util.List;

public class GameManager {
    List<Game> activeGames;
    List<Game> waitingGames;

    public void createNewGame(){
        //Random map not in use
        //new game
    }

    public void join(Player player, GameEnums.GameMode gameMode){
        //check if player is in a game already
        //if they are, make them leave their game to join this one
        //if in a party, do the same to the members of their party
        switch(gameMode){
            case PARTY:
                //Create a new game
                //Add all players in the party
                //open GUI for leader
                break;
            case ONE_V_ONE:
                //only allow joining if not in party or in party of 1
                //Find game
                //if no game, create game
                //if game is full, start it
                break;
            case TWO_V_TWO:
                //only allow joining in party of 2
                //find game
                //if no game, make new game
                //add both members of party
                //start game if its full now
                break;
        }
    }

    public void leaveGame(Player player){
        //maybe unnecessary, just call removePlayer in the game itself
    }

    public void cleanUpGames(){
        //update activeGames list to only include games that are in progress
        //update waitingGames to only have games that are waiting for players
        //Clean up any games that have ended (idk)
    }

    public Game getPlayerGame(Player player){
        //iterate through active games, and get playerlist for each one
        //if playerlist has the player's game then add them
        return null;}


    public List<Game> getActiveGames() {return activeGames;}
    public void setActiveGames(List<Game> activeGames) {this.activeGames = activeGames;}
}
