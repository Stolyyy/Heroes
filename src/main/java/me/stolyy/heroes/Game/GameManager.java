package me.stolyy.heroes.Game;

import org.bukkit.entity.Player;

import java.util.List;

public class GameManager {
    List<Game> activeGames;

    public void createNewGame(){
        //Random map not in use
        //
    }

    public void join(Player player, GameEnums.GameMode gameMode){
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
                //
                break;
        }
    }

    public void leaveGame(Player player){}

    public Game getPlayerGame(Player player){return null;}


    public List<Game> getActiveGames() {return activeGames;}
    public void setActiveGames(List<Game> activeGames) {this.activeGames = activeGames;}
}
