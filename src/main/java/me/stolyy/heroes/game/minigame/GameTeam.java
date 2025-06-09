package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.heros.HeroManager;
import org.bukkit.entity.Player;

import java.util.*;

public class GameTeam {
    private final Set<Player> players = new HashSet<>();
    private final Map<Player, Integer> lives = new HashMap<>();
    private final GameEnums.TeamColor color;
    private TeamSettings settings = new TeamSettings();

    public GameTeam(GameEnums.TeamColor color) {
        this.color = color;
    }

    public void initialize(){
        if(settings.randomizeHeroes()) {
            HeroManager.randomizeHeroes(players());
        }
        for(Player p : players){
            lives.put(p, settings.lives());
        }
    }

    //add, remove, query
    public void add(Player player){
        players.add(player);
    }

    public void remove(Player player){
        players.remove(player);
    }

    public boolean contains(Player player){
        return players.contains(player);
    }


    public void clearPlayers(){
        players.clear();
    }



    public int lives(Player player){
        return lives.getOrDefault(player, 0);
    }

    public void subtractLife(Player player){
        lives.put(player, lives.get(player) - 1);
    }

    public Set<Player> players() {
        return Collections.unmodifiableSet(players);
    }

    public GameEnums.TeamColor color() {
        return color;
    }

    public TeamSettings settings() {
        return settings;
    }

    public GameTeam setSettings(TeamSettings settings) {
        this.settings = settings;
        return this;
    }
}
