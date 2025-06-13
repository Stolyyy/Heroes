package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.utility.effects.Equipment;
import me.stolyy.heroes.game.minigame.GameEnums.GameMode;
import me.stolyy.heroes.game.minigame.GameEnums.TeamColor;
import me.stolyy.heroes.game.minigame.GameEnums.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.*;


public class Game {
    static final int RESPAWN_TIME = 7; //seconds
    private final GameMode gameMode;
    private GameState gameState;
    private final GameSettings settings;
    private final GameVisuals visuals;
    private final Map<TeamColor, GameTeam> teams = new HashMap<>();

    public Game(GameMap gameMap, GameMode gameMode) {
        this.gameMode = gameMode;
        settings = new GameSettings(gameMap);
        visuals = new GameVisuals(this);
        gameState = GameState.WAITING;

        teams.put(TeamColor.RED, new GameTeam(TeamColor.RED));
        teams.put(TeamColor.BLUE, new GameTeam(TeamColor.BLUE));
        teams.put(TeamColor.SPECTATOR, new GameTeam(TeamColor.SPECTATOR));
        if(gameMode == GameMode.PARTY){
            teams.put(TeamColor.GREEN, new GameTeam(TeamColor.GREEN));
            teams.put(TeamColor.YELLOW, new GameTeam(TeamColor.YELLOW));
        }
    }


    //GAMESTATE METHODS


    public void setGameState(GameState newState) {
        if(gameState == newState) return;
        switch(newState) {
            case WAITING -> {
               visuals.reset();
            }
            case STARTING -> {
                visuals.showCountdown();
            }
            case IN_PROGRESS -> {
                visuals.startTimer();
            }
            case ENDED -> {
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::clean, 20 * RESPAWN_TIME);
            }
        }

        GameManager.updateGameStatus(this);
        gameState = newState;
    }

    public void countdown() {
        setGameState(GameState.STARTING);
        for(GameTeam team : teams.values()){
            team.initialize();
            for(Player p : team.players()){
                GameEffects.restrictPlayer(p);
                GameEffects.applyEffects(p, teams.get(playerTeam(p)).settings());
                Equipment.equip(p);
                HeroManager.getHero(p).onCountdown();
            }
        }
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::startGame, 20 * 10);
    }

    public void startGame() {
        setGameState(GameState.IN_PROGRESS);
        for(Player p : alivePlayers()) {
            GameEffects.unRestrictPlayer(p);
            HeroManager.getHero(p).onGameStart();
        }
    }

    public void endGame() {
        if(teamsWithPlayers() >= 2) {
            visuals.draw();
        } else {
            for (GameTeam team : teams.values()) {
                if (team.color() == TeamColor.SPECTATOR) continue;
                if(!team.players().isEmpty()) visuals.win(team);
            }
        }
        setGameState(GameState.ENDED);
    }

    public boolean canCountdown() {
        return teamsWithPlayers() >= 2;
    }

    public void checkGameEnd() {
        if(gameState == GameState.ENDED) return;

        if(teamsWithPlayers() < 2) {
            endGame();
        }
    }

    public void clean() {
        for(Player p : allPlayers()){
            if(GameManager.getPlayerGame(p) == this){
                Heroes.getInstance().teleportToLobby(p);
                GameManager.removePlayerGame(p);
            }
        }
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            teams.values().forEach(GameTeam::clearPlayers);
            visuals.reset();
            GameMapManager.deleteWorld(settings.map());
        }, 30);
    }


    //PLAYER METHODS


    public void addPlayer(Player player) {
        if (gameState != GameState.WAITING) return;

        switch(gameMode) {
            case ONE_V_ONE -> {
                add1v1(player);
            } case TWO_V_TWO -> {
                add2v2(player);
            } case PARTY -> {
                addParty(player);
            }
        }
    }

    public void removePlayer(Player player) {
        GameTeam team = teams.get(playerTeam(player));
        if(team == null) return;
        team.remove(player);
        visuals.update();
        GameEffects.removeEffects(player);
        HeroManager.getHero(player).onElimination();
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        checkGameEnd();
    }

    public void changeTeam(Player player, TeamColor teamColor) {
        GameTeam oldTeam = teams.get(playerTeam(player));
        if(oldTeam != null) oldTeam.remove(player);

        if(teamColor == TeamColor.SPECTATOR) addSpectator(player);
        else addPlayer(player, teamColor);
    }

    public void addSpectator(Player player) {
        addPlayer(player, TeamColor.SPECTATOR);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        GameEffects.unRestrictPlayer(player);
    }

    public void playerDeath(Player player) {
        if (gameState != GameState.IN_PROGRESS) return;

        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        GameEffects.restrictPlayer(player);
        HeroManager.getHero(player).onDeath();
        visuals.respawning(player);

        GameTeam team = teams.get(playerTeam(player));
        team.subtractLife(player);
        if(team.lives(player) <= 0){
            changeTeam(player, TeamColor.SPECTATOR);
            HeroManager.getHero(player).onElimination();
            checkGameEnd();
        } else Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> playerRespawn(player), 20 * RESPAWN_TIME);
    }

    public void playerRespawn(Player player) {
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        GameEffects.unRestrictPlayer(player);
        GameEffects.applyEffects(player, teams.get(playerTeam(player)).settings());
        HeroManager.getHero(player).onRespawn();
    }


    //Getters


    public Set<Player> allPlayers() {
        Set<Player> set = new HashSet<>();
        for (GameTeam team : teams.values()) {
            set.addAll(team.players());
        }
        return Collections.unmodifiableSet(set);
    }

    public Set<Player> alivePlayers() {
        Set<Player> set = new HashSet<>();
        for (GameTeam team : teams.values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;
            set.addAll(team.players());
        }
        return set;
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public GameState gameState() {
        return gameState;
    }

    public GameSettings settings(){
        return settings;
    }

    public int lives(Player player){
        return teams.get(playerTeam(player)).lives(player);
    }

    public Location furthestSpawn(Player player){
        Set<Player> enemies = alivePlayers();
        enemies.removeAll(teams.get(playerTeam(player)).players());
        return settings.map().getFurthestSpawn(player, enemies);
    }

    public BoundingBox mapBounds(){
        return settings.map().getBoundaries();
    }

    public boolean sameTeam(Player first, Player second){
        return playerTeam(first) == playerTeam(second);
    }

    public boolean friendlyFire(Player player){
        return teams.get(playerTeam(player)).settings().friendlyFire();
    }

    public TeamColor playerTeam(Player player) {
        for (GameTeam team : teams.values()) {
            if(team.contains(player)) return team.color();
        }
        return null;
    }

    public Map<TeamColor, GameTeam> teams(){
        return Collections.unmodifiableMap(teams);
    }

    public void copyTeamSettingsToAllTeams(TeamSettings teamSettings){
        for(GameTeam team : teams.values()){
            if(team.color() == TeamColor.SPECTATOR) continue;
            team.setSettings(teamSettings);
        }
    }

    //HELPERS


    private void teleport(Player player){
        TeamColor teamColor = playerTeam(player);
        Location teleportLocation;
        GameMap map = settings.map();

        int index = 0;
        if(teamColor == TeamColor.BLUE) index = 1;
        else if(teamColor == TeamColor.GREEN) index = 2;
        else if(teamColor == TeamColor.YELLOW) index = 3;

        if(teamColor == TeamColor.SPECTATOR) teleportLocation = map.getSpectatorLocation();
        else teleportLocation = map.getSpawnLocations()[index].clone();

        int teamSize = alivePlayersPerTeam().get(teamColor);
        if(teamSize == 1) player.teleport(teleportLocation);
        else if(teamSize == 2) player.teleport(teleportLocation.add(1,0,0));
        else player.teleport(teleportLocation.add(-1,0,0));
    }

    private void add1v1(Player player) {
        if(alivePlayers().isEmpty()){
            addPlayer(player, TeamColor.RED);
        } else {
            addPlayer(player, TeamColor.BLUE);
        }
    }

    private void add2v2(Player player) {
        Party party = PartyManager.getPlayerParty(player);
        TeamColor team = alivePlayers().isEmpty() ? TeamColor.RED : TeamColor.BLUE;
        for(Player p : party.getMembers())
            addPlayer(p, team);
    }

    private void addParty(Player player) {
        Party party = PartyManager.getPlayerParty(player);
        addPlayer(player, TeamColor.RED);
        int count = 1;
        for(Player p : party.getMembers()){
            if(p.equals(player)) continue;
            if (count == 1) addPlayer(p, TeamColor.BLUE);
            else addSpectator(p);
            count++;
        }
    }

    public void addPlayer(Player player, TeamColor teamColor) {
        GameManager.setPlayerGame(player, this);
        teams.get(teamColor).add(player);
        visuals.update();
        GameEffects.restrictPlayer(player);
        teleport(player);
    }

    private Set<Player> spectators() {
        return teams.get(TeamColor.SPECTATOR).players();
    }

    private Map<TeamColor, Integer> alivePlayersPerTeam() {
        Map<TeamColor, Integer> map = new HashMap<>();
        for (GameTeam team : teams.values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;
            map.put(team.color(), team.players().size());
        }
        return map;
    }

    private int teamsWithPlayers(){
        int teamsWithPlayers = 0;
        for(Integer i : alivePlayersPerTeam().values())
            if(i > 0) teamsWithPlayers++;
        return teamsWithPlayers;
    }
}