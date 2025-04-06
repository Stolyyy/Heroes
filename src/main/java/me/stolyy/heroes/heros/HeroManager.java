package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.characters.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

public class HeroManager {
    private static final Map<Player, Hero> heroes = new HashMap<>();

    public static void setHero(Player player, Hero hero) {
        if(heroes.get(player) instanceof HeroEnergy oldHero)
            oldHero.canIncreaseEnergy = false;
        heroes.put(player, hero);
    }

    public static Hero getHero(Player player) {
        if(heroes.get(player) == null){
            Hero hero = new Shoop(player);
            Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
            heroes.put(player, hero);
        }
        return heroes.get(player);
    }

    public static void removePlayer(Player player) {
        heroes.remove(player);
    }

    public static String heroToString(Hero hero){
        return switch (hero) {
            case VoidCrawler voidCrawler -> "Void Crawler";
            case Shoop shoop -> "Shoop";
            case Skullfire skullfire -> "Skullfire";
            case Pug pug -> "Pug";
            case Blud blud -> "blud";
            case Bug bug -> "Bug";
            case Bulk bulk -> "Bulk";
            case Punishgers gers -> "Herlgers";
            case Spooderman spooderman -> "Spooderman";
            case null, default -> "Invalid Hero";
        };
    }

    public static void clear() {
        heroes.clear();
    }
}
