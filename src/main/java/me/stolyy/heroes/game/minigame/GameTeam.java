package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.heros.HeroManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class GameTeam {
    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final GameEnums.TeamColor color;
    private TeamSettings settings = new TeamSettings();

    public GameTeam(GameEnums.TeamColor color) {
        this.color = color;
    }

    public void initialize(){
        if(settings.randomizeHeroes()) {
            HeroManager.randomizeHeroes(onlinePlayers());
        }
        for(Player p : onlinePlayers()){
            lives.put(p.getUniqueId(), settings.lives());
        }
    }



    public void add(Player player){
        players.add(player.getUniqueId());
        lives.put(player.getUniqueId(), settings.lives());
    }

    public void remove(Player player){
        players.remove(player.getUniqueId());
        lives.remove(player.getUniqueId());
    }

    public boolean contains(Player player){
        return players.contains(player.getUniqueId());
    }

    public Set<Player> onlinePlayers() {
        return players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<UUID> playerUUIDs() {
        return Collections.unmodifiableSet(players);
    }

    public int lives(Player player){
        return lives.getOrDefault(player.getUniqueId(), 0);
    }

    public int totalLives() {
        return lives.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void subtractLife(Player player){
        lives.put(player.getUniqueId(), lives.get(player.getUniqueId()) - 1);
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
