package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.heros.HeroCooldown;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.utility.Equipment;
import me.stolyy.heroes.game.minigame.GameEnums.GameMode;
import me.stolyy.heroes.game.minigame.GameEnums.GameTeam;
import me.stolyy.heroes.game.minigame.GameEnums.GameState;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;


public class Game {
    private Set<Player> playerList = new HashSet<>();
    private Set<Player> alivePlayerList = new HashSet<>();
    private Set<Player> restrictedPlayers = new HashSet<>();
    private GameMode gameMode;
    private Map<Player, GameTeam> playerTeams = new HashMap<>();
    private GameMap gameMap;
    private GameState gameState;
    private Map<Player, Integer> lives = new HashMap<>();
    private Scoreboard scoreboard;
    private Objective tabHealthObjective;
    private Objective belowNameHealthObjective;
    private Objective objective;

    public Game(GameMap gameMap, GameMode gameMode) {
        this.gameMap = GameMapManager.createWorld(gameMap);
        this.gameMode = gameMode;
        gameState = GameState.WAITING;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        this.tabHealthObjective = scoreboard.registerNewObjective("tabHealth", "health", "❤");
        this.tabHealthObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        this.belowNameHealthObjective = scoreboard.registerNewObjective("belowNameHealth", "health", "❤");
        this.belowNameHealthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);

        objective = scoreboard.registerNewObjective("GameInfo", "dummy", ChatColor.GOLD + "SMASH HEROES 4");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void addPlayer(Player player){
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        switch(gameMode) {
            case GameMode.PARTY:
                playerList.add(player);
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
        updateVisuals();
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
        tabHealthObjective.getScoreboard().resetScores(player.getName());
        belowNameHealthObjective.getScoreboard().resetScores(player.getName());
        updateVisuals();
        //TODO: remove player's hero
        Heroes.getInstance().teleportToLobby(player);
    }

    public void countdown(){
        GameManager.waitingGames.remove(this);
        GameManager.activeGames.add(this);
        gameState = GameState.STARTING;
        for(Player p : playerList) {
            if(playerTeams.getOrDefault(p, GameTeam.SPECTATOR) != GameTeam.SPECTATOR){
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
            if(HeroManager.getHero(p) instanceof HeroCooldown h)
                h.resetUltTimer();
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

        //null iteration around here somewhere
        for (Player p : playerList) {
            if(GameManager.getPlayerGame(p) == this)
                Heroes.getInstance().teleportToLobby(p);
            removePlayer(p);
            if (p.getScoreboard() == scoreboard) p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()); // ✅ Clears scoreboard
        }
        gameState = GameState.ENDED;
        GameManager.cleanUpGames();
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            //HAVEN'T TESTED YET
            //clear();
            GameMapManager.deleteWorld(gameMap);
        }, 100L);
    }

    public void onDeath(Player player){
        //handle respawn logic (timer, respawn point, restricting movement)
        lives.put(player, lives.get(player) - 1);
        //detect nearest enemy to dead player
        Location furthestSpawn = getFurthestSpawn(player);
        player.setRespawnLocation(furthestSpawn, true);
            //respawn player if they still have lives
        checkGameEnd();
        updateVisuals();
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
                    } else
                        player.sendTitle("Respawning in " + timer, "", 2, 20, 5);
                }
            }.runTaskTimer(Heroes.getInstance(), 10L, 19L);
            //make player a spectator if they ran out of lives
        } else if(lives.get(player) < 1) {
            addSpectator(player);
        }
    }

    public Location getFurthestSpawn(Player player){
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player enemy : alivePlayerList) {
            if (playerTeams.get(enemy) != playerTeams.get(player) && enemy.getWorld() == player.getWorld()) {
                double distance = enemy.getLocation().distance(player.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = enemy;
                }
            }
        }
        //respawn player at furthest spawn point from enemy location (or player location if no enemy)
        double furthestDistance = 0;
        Location referencePoint = player.getLocation();
        Location furthestSpawn = gameMap.getSpawnLocations()[0];
        if (nearestPlayer != null) referencePoint = nearestPlayer.getLocation();

        for (Location spawn : gameMap.getSpawnLocations()) {
            double distance = referencePoint.distance(spawn);
            if (distance > furthestDistance){
                furthestDistance = distance;
                furthestSpawn = spawn;
            }
        }
        return furthestSpawn;
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
        if(gameState != GameState.ENDED && teamsWithPlayers <2){
            //find winning team
                gameState = GameState.ENDED;
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
                String message = "The winning team is: " + winningTeam + " team!";
                for (Player player : playerList) {
                    player.sendMessage("Game Over!");
                    player.sendMessage(message);
                    //TODO: Add statlines (Kills, Damage, Damage + DMG by Void)
                }
            }
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::gameEnd, (5 * 20L));
        }
    }

    public void checkPosition(Player player){
        if (!gameMap.getBoundaries().contains(player.getLocation().toVector())) {
            onDeath(player);
        }
    }

    public void updateVisuals() {
        // Ensure the scoreboard objective exists
        Objective objective = scoreboard.getObjective("GameInfo");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("GameInfo", "dummy", ChatColor.GOLD + "SMASH HEROES 4");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Register teams only if they don't already exist
        for (GameTeam team : GameTeam.values()) {
            if (team == GameTeam.SPECTATOR) continue; // Skip spectators

            Team scoreboardTeam = scoreboard.getTeam(team.name());
            if (scoreboardTeam == null) {
                scoreboardTeam = scoreboard.registerNewTeam(team.name());
            }

            // Set team prefix with color
            scoreboardTeam.setColor(getTeamChatColor(team));
        }

        // Assign players to their teams and update scoreboard scores
        for (Player player : playerTeams.keySet()) {
            if (!player.getWorld().equals(getMap().getWorld())) continue; // ✅ Ignore players outside the game world

            GameTeam team = playerTeams.get(player);
            if (team == GameTeam.SPECTATOR) continue; // Skip spectators

            Team scoreboardTeam = scoreboard.getTeam(team.name());
            if (scoreboardTeam != null) {
                scoreboardTeam.addEntry(player.getName());
            }

            // Ensure we update the correct player's score without duplication
            Score score = objective.getScore(player.getName());
            score.setScore(lives.getOrDefault(player, 0));
        }

        // Apply scoreboard only to players in the game's world
        for (Player player : playerList) {
            if (player.getWorld().equals(getMap().getWorld())) {
                player.setScoreboard(scoreboard);
            }
        }
    }



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
                alivePlayerList.add(player);
                break;
            case GameTeam.BLUE:
                player.teleport(gameMap.getSpawnLocations()[1]);
                alivePlayerList.add(player);
                break;
            case GameTeam.GREEN:
                player.teleport(gameMap.getSpawnLocations()[2]);
                alivePlayerList.add(player);
                break;
            case GameTeam.YELLOW:
                player.teleport(gameMap.getSpawnLocations()[3]);
                alivePlayerList.add(player);
                break;
            case GameTeam.SPECTATOR:
                addSpectator(player);
                player.teleport(gameMap.getSpectatorLocation());
                alivePlayerList.remove(player);
                break;
        }
    }

    public boolean canCountdown(){
        Map<GameTeam, Integer> teamCounts = getTeamPlayerCount();
        int teamsWithPlayers = 0;
        for (int count : teamCounts.values()) {
            if (count > 0) {
                teamsWithPlayers++;
            }
        }
        return teamsWithPlayers >= 2;
    }

    private ChatColor getTeamChatColor(GameTeam team) {
        switch (team) {
            case RED:
                return ChatColor.RED;
            case BLUE:
                return ChatColor.BLUE;
            case GREEN:
                return ChatColor.GREEN;
            case YELLOW:
                return ChatColor.YELLOW;
            default:
                return ChatColor.WHITE;
        }
    }

    private void clear(){
        playerList.clear();
        alivePlayerList.clear();
        restrictedPlayers.clear();
        playerTeams.clear();
        lives.clear();
    }

    public void restrictPlayer(Player player){restrictedPlayers.add(player);}
    public void unRestrictPlayer(Player player){restrictedPlayers.remove(player);}
    public boolean isPlayerRestricted(Player player) {return restrictedPlayers.contains(player); }
    public Set<Player> getPlayerList() {return playerList;}
    public void setPlayerList(Set<Player> playerList) {this.playerList = playerList;}
    public Set<Player> getAlivePlayerList() {return alivePlayerList;}
    public void setAlivePlayerList(Set<Player> alivePlayerList) {this.alivePlayerList = alivePlayerList;}
    public GameMode getGameMode() {return gameMode;}
    public void setGameMode(GameMode gameMode) {this.gameMode = gameMode;}
    public Map<Player, GameTeam> getPlayerTeams() {return playerTeams;}
    public void setPlayerTeams(Map<Player, GameTeam> playerTeams) {this.playerTeams = playerTeams;}
    public GameMap getMap() {return gameMap;}
    public void setMap(GameMap gameMap) {this.gameMap = gameMap;}
    public GameState getGameState() {return gameState;}
    public void setGameState(GameState gameState) {this.gameState = gameState;}
}
