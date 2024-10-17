package me.stolyy.heroes;

import me.stolyy.heroes.heros.*;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeroManager {
    private final Map<Player, Hero> heroes;

    public HeroManager() {
        heroes = new HashMap<>();
    }

    public void setHero(Player player, Hero hero) {
        if(heroes.getOrDefault(player, null) instanceof Energy oldHero) {
            oldHero.setCanIncreaseEnergy(player, false);
            oldHero.removeEnergyData(player);
        }
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
        return switch (h) {
            case VoidCrawler voidCrawler -> "Void Crawler";
            case Shoop shoop -> "Shoop";
            case Skullfire skullfire -> "Skullfire";
            case Pug pug -> "Pug";
            case null, default -> "Invalid Hero";
        };
    }

    public void clear() {
        heroes.clear();
    }
}
