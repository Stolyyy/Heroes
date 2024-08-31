package me.stolyy.heroes;

import me.stolyy.heroes.heros.*;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeroManager {
    private final Map<Player, Hero> heroes;

    public HeroManager() {
        heroes = new ConcurrentHashMap<>();
    }

    public void setHero(Player player, Hero hero) {
        heroes.put(player, hero);
    }

    public Hero getHero(Player player) {
        return heroes.get(player);
    }

    public void removePlayer(Player player) {
        heroes.remove(player);
    }

    public String heroToString(Player player){
        Hero h = getHero(player);
        if(h instanceof VoidCrawler){
            return "Void Crawler";
        } else if(h instanceof Shoop){
            return "Shoop";
        }else if(h instanceof Skullfire){
            return "Skullfire";
        } else if(h instanceof Pug){
            return "Pug";
        } else {
            return "Invalid Hero";
        }
    }

    public void clear() {
        heroes.clear();
    }
}
