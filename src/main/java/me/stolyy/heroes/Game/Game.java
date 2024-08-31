package me.stolyy.heroes.Game;

import me.stolyy.heroes.Utility.Equipment;
import me.stolyy.heroes.Game.GameEnums.GameMode;
import me.stolyy.heroes.Game.GameEnums.GameTeam;
import me.stolyy.heroes.Game.GameEnums.GameState;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;


public class Game {
    List<Player> playerList;
    List<Player> alivePlayerList;
    GameMode gameMode;
    Map<Player, GameTeam> playerTeams;
    GameMap gameMap;
    GameState gameState;
    Map<Player, Integer> lives;

    public Game(GameMap gameMap, GameMode gameMode) {
        this.gameMap = gameMap;
        this.gameMode = gameMode;
        gameState = GameState.WAITING;
    }

    public void addPlayer(Player player){
        switch(gameMode) {
            case GameMode.PARTY:
                addSpectator(player);
                player.teleport(gameMap.getSpawnLocations()[3]);
                break;

            case GameMode.ONE_V_ONE:
                if (getTeamPlayerCount().getOrDefault(GameTeam.RED, 0) > 0) {
                    playerTeams.put(player, GameTeam.BLUE);
                    player.teleport(gameMap.getSpawnLocations()[1]);
                } else {
                    playerTeams.put(player, GameTeam.RED);
                    player.teleport(gameMap.getSpawnLocations()[0]);
                }
                alivePlayerList.add(player);
                playerList.add(player);

                if(alivePlayerList.size() >=2) countdown();
                break;

            case GameMode.TWO_V_TWO:
                switch(getTeamPlayerCount().getOrDefault(GameTeam.RED, 0)){
                    case 0:
                        playerTeams.put(player, GameTeam.RED);
                        player.teleport(gameMap.getSpawnLocations()[0].add(0.5,0,0));
                        break;
                    case 1:
                        playerTeams.put(player, GameTeam.RED);
                        player.teleport(gameMap.getSpawnLocations()[0].add(-0.5,0,0));
                        break;
                    case 2:
                        playerTeams.put(player, GameTeam.BLUE);
                        player.teleport(gameMap.getSpawnLocations()[1].add(0.5,0,0));
                        break;
                    case 3:
                        playerTeams.put(player, GameTeam.BLUE);
                        player.teleport(gameMap.getSpawnLocations()[1].add(-0.5,0,0));
                        break;
                }
                alivePlayerList.add(player);
                playerList.add(player);
                if(alivePlayerList.size() >=4) countdown();
                break;
        }
        restrictPlayer(player);
        lives.put(player, 3);
        //Add player if game is not full
        //add effects and restrict
        //if game is now full, initiate countdown
        //make sure proper team is chosen (1 per team in 1v1, 2 per team in 2v2 (add party teammate), start as spectator in party
    }

    public void addSpectator(Player player){
        if(!playerList.contains(player)) playerList.add(player);
        alivePlayerList.remove(player);
        playerTeams.put(player, GameTeam.SPECTATOR);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        unRestrictPlayer(player);
        removeEffects(player);
        //add player to spec team
        //unrestrict and remove effects
        //put them in spec
    }

    public void removePlayer(Player player){
        //remove from playerlist and playerteams
        //send them to lobby
        //checkgameend
        //uninstantialize heroes
        playerList.remove(player);
        playerTeams.remove(player);
        checkGameEnd();
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        removeEffects(player);
        unRestrictPlayer(player);
        updateVisuals();
        //TODO: remove player's hero
        Heroes.getInstance().teleportToLobby(player);
    }

    public void countdown(){
        gameState = GameState.STARTING;
        for(Player p : playerList) {
            if(playerTeams.getOrDefault(p, GameTeam.SPECTATOR) != GameTeam.SPECTATOR){
            //TODO: set hero
            Equipment.equip(p);
        }}
        new BukkitRunnable() {
            private int timer = 10;
            @Override
            public void run() {
                timer--;
                if(timer <= 0){
                    cancel();
                    for(Player p : playerList) p.sendTitle("GO!", "Match Started", 2, 10, 5);
                    gameState = GameState.IN_PROGRESS;
                    gameStart();
                }
                for(Player p : playerList) {
                    p.sendTitle("Starting in " + timer, "", 2, 20, 5);
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 10L, 19L);
        updateVisuals();
    }

    public void gameStart(){
        for(Player p : playerList) {
            applyEffects(p);
            unRestrictPlayer(p);
            updateVisuals();
        }
        gameState = GameState.IN_PROGRESS;
        //unrestrict players
        //unrestrict abilities
    }

    public void gameEnd(){
        //remove all players
        //delete game (if possible)
        //send everyone to lobby
        //remove effects
        for(Player p : playerList) {
            Heroes.getInstance().teleportToLobby(p);
            removePlayer(p);
        }
        gameState = GameState.ENDED;
    }

    public void onDeath(Player player){
        //handle respawn logic (timer, respawn point, restricting movement)
        lives.put(player, lives.get(player) - 1);
        //detect nearest enemy to dead player
            Player nearestPlayer = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Player enemy : alivePlayerList) {
                if (playerTeams.get(enemy) != playerTeams.get(player)) {
                    double distance = enemy.getLocation().distance(player.getLocation());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestPlayer = player;
                    }
                }
            }
            //respawn player at furthest spawn point from enemy location (or player location if no enemy)
            double nearestSpawn = 0;
            Location referencePoint = player.getLocation();
            if (nearestPlayer != null) referencePoint = nearestPlayer.getLocation();

            for (Location spawn : gameMap.getSpawnLocations()) {
                double distance = referencePoint.distance(spawn);
                if (distance > nearestSpawn) nearestSpawn = distance;
            }
            //respawn player if they still have lives
        if(lives.get(player) > 0) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            restrictPlayer(player);
            new BukkitRunnable() {
                private int timer = 6;
                @Override
                public void run() {
                    timer--;
                    if(timer <= 0){
                        cancel();
                        player.sendTitle("Welcome Back,", "Go!", 2, 10, 5);
                        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                        unRestrictPlayer(player);
                    }
                        player.sendTitle("Respawning in " + timer, "", 2, 20, 5);
                }
            }.runTaskTimer(Heroes.getInstance(), 10L, 19L);
            //make player a spectator if they ran out of lives
        } else if(lives.get(player) < 1) {
            addSpectator(player);
        }
        checkGameEnd();
        updateVisuals();
    }

    public void checkGameEnd(){
        //Go through playerTeams
        //increment an int for each team with a player left
        //if it = 1, then game end

        Map<GameTeam, Integer> teamCounts = getTeamPlayerCount();
        int teamsWithPlayers = 0;
        // Iterate through the teamCounts map
        for (int count : teamCounts.values()) {
            if (count > 0) {
                teamsWithPlayers++;
            }
        }
        //end game if only one team has players
        if(teamsWithPlayers<2){
            //find winning team
                GameTeam winningTeam = null;
                Set<GameTeam> teamsWithPlayersLeft = new HashSet<>();
                for (Map.Entry<GameTeam, Integer> entry : teamCounts.entrySet()) {
                    if (entry.getValue() > 0) {
                        teamsWithPlayersLeft.add(entry.getKey());
                    }
                }
                if (!teamsWithPlayersLeft.isEmpty()) {
                    winningTeam = teamsWithPlayersLeft.iterator().next();
                }
            if (winningTeam != null) {
                String message = "The winning team is: " + winningTeam + "team!";
                for (Player player : playerList) {
                    player.sendMessage("Game Over!");
                    player.sendMessage(message);
                    //TODO: Add statlines (Kills, Damage, Damage + DMG by Void)
                }
            }
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::gameEnd, (5 * 20L));
        }
    }

    public void updateVisuals(){
        //update HP on tab and below name
        //update scoreboard
    }

    public void restrictPlayer(Player player){
    }

    public void unRestrictPlayer(Player player){}

    public void applyEffects(Player player){
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(100.0);
        player.setHealth(100.0);
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0));
    }

    public void removeEffects(Player player){
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setHealthScaled(false);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.SATURATION);
    }

    //returns a map with the number of players on each team
    public Map<GameTeam, Integer> getTeamPlayerCount() {
        Map<GameTeam, Integer> teamCounts = new HashMap<>();
        for (GameTeam team : playerTeams.values()) {
            if (team != GameTeam.SPECTATOR) {
                int currentCount = teamCounts.getOrDefault(team, 0);
                teamCounts.put(team, currentCount + 1);
            }
        }
        return teamCounts;
    }

    public void setPlayerTeam(Player player, GameTeam gameTeam){
        playerTeams.put(player, gameTeam);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        switch (gameTeam){
            case GameTeam.RED:
                player.teleport(gameMap.getSpawnLocations()[0]);
                break;
            case GameTeam.BLUE:
                player.teleport(gameMap.getSpawnLocations()[1]);
                break;
            case GameTeam.GREEN:
                player.teleport(gameMap.getSpawnLocations()[2]);
                break;
            case GameTeam.YELLOW:
                player.teleport(gameMap.getSpawnLocations()[3]);
                break;
            case GameTeam.SPECTATOR:
                addSpectator(player);
                player.teleport(gameMap.getSpectatorLocation());
                break;
        }
    }

    public List<Player> getPlayerList() {return playerList;}
    public void setPlayerList(List<Player> playerList) {this.playerList = playerList;}
    public GameMode getGameMode() {return gameMode;}
    public void setGameMode(GameMode gameMode) {this.gameMode = gameMode;}
    public Map<Player, GameTeam> getPlayerTeams() {return playerTeams;}
    public void setPlayerTeams(Map<Player, GameTeam> playerTeams) {this.playerTeams = playerTeams;}
    public GameMap getMap() {return gameMap;}
    public void setMap(GameMap gameMap) {this.gameMap = gameMap;}
    public GameState getGameState() {return gameState;}
    public void setGameState(GameState gameState) {this.gameState = gameState;}
}
