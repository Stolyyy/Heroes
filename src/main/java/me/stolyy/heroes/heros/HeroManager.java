package me.stolyy.heroes.heros;

import it.unimi.dsi.fastutil.objects.ObjectSets;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.characters.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HeroManager {
    private static final Map<Player, Hero> heroes = new HashMap<>();
    private static final Map<Player, Set<Bug.Charms>> charms = new HashMap<>();

    public static void setHero(Player player, Hero hero) {
        Hero oldHero = heroes.get(player);
        ((HeroEnergy)oldHero).setCanIncreaseEnergy(false);
        ((HeroCooldown) oldHero).cancelTasks();

        heroes.put(player, hero);
    }

    public static HeroEnergy getHero(Player player) {
        if(heroes.get(player) == null){
            Hero hero = new Shoop(player);
            Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
            heroes.put(player, hero);
        }
        return (HeroEnergy) heroes.get(player);
    }

    public static void removePlayer(Player player) {
        heroes.remove(player);
    }

    public static void randomizeHeroes(Set<Player> players){
        for (Player player : players) {
            Hero hero = switch ((int) (Math.random() * 4)) {
                case 0 -> new Pug(player);
                case 1 -> new Shoop(player);
                case 2 -> new Skullfire(player);
                case 3 -> new VoidCrawler(player);
                case 4 -> new Bulk(player);
                case 5 -> new Spooderman(player);
                case 6 -> new Punishgers(player);
                default -> new Shoop(player);
            };
            if(hero instanceof Listener)
                Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
            setHero(player, hero);
        }
    }

    public static void setCharms(Player player, Set<Bug.Charms> charmSet){
        charms.put(player, charmSet);
    }

    public static Set<Bug.Charms> getCharms(Player player){
        if(charms.get(player) == null) return new HashSet<>();
        return charms.get(player);
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
