package me.stolyy.heroes.Game;

import me.stolyy.heroes.Game.Menus.Party;
import me.stolyy.heroes.Game.Party.PartyManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

public class GameManager {
    Set<Game> activeGames;
    Set<Game> waitingGames;


    public Game createNewGame(GameEnums.GameMode gameMode){
        //Random map not in use
        //new game
        return new Game(GameMapManager.getRandomMap(), gameMode);
    }

    public void join(Player player, GameEnums.GameMode gameMode){

        //check if player is in a game already
        //if they are, make them leave their game to join this one
        //if in a party, do the same to the members of their party

        switch(gameMode){
            case PARTY:
                Game game1 = createNewGame(gameMode);
                for(Player p : PartyManager.getPlayersInParty(player)){
                    game1.addPlayer(p);
                }
                Party.openGUI(player);
                //Create a new game
                //Add all players in the party
                //open GUI for leader
                break;
            case ONE_V_ONE:
                //only allow joining if not in party or in party of 1
                //Find game
                //if no game, create game
                //if game is full, start it
                if(!PartyManager.isInParty(player)){
                    boolean foundGame = false;
                    if(!waitingGames.isEmpty()){
                        loop: for(Game g : waitingGames){
                            if(g.getGameMode().equals(gameMode)){
                                g.addPlayer(player);
                                foundGame = true;
                            }
                            break loop;
                        }
                    }
                    if(!foundGame){
                        Game game2 = createNewGame(gameMode);
                        game2.addPlayer(player);
                        if(game2.getPlayerList().size() == 2) game2.gameStart();
                    }
                } else{
                    player.sendMessage("You cannot enter 1v1 in a party, please join Party for custom games");
                }
                break;
            case TWO_V_TWO:
                //only allow joining in party of 2
                //find game
                //if no game, make new game
                //add both members of party
                //start game if its full now
                if(PartyManager.getPartySize(player) == 2){
                    boolean foundGame = false;
                    if(!waitingGames.isEmpty()){
                        loop: for(Game g : waitingGames){
                            if(g.getGameMode().equals(gameMode)){
                                g.addPlayer(player);
                                foundGame = true;
                            }
                            break loop;
                        }
                    }
                    if(!foundGame){
                        Game game2 = createNewGame(gameMode);
                        game2.addPlayer(player);
                        for(Player p : PartyManager.getPartyMembers(player)){
                            if(!p.equals(player)) game2.addPlayer(p);
                        }

                        if(game2.getPlayerList().size() == 4) game2.gameStart();
                    }
                }
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
        for(Game game : activeGames) {
            if (game.getPlayerList().contains(player)) return game;
        }
        return null;
        }


    public Set<Game> getActiveGames() {return activeGames;}
    public void setActiveGames(Set<Game> activeGames) {this.activeGames = activeGames;}
}
