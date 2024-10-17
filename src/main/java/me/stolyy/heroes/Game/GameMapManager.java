package me.stolyy.heroes.Game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.*;

public class GameMapManager {
    static Set<GameMap> maps;

    public static void initializeMaps(){

    }

    public static GameMap createWorld(){

    }

    public static GameMap deleteWorld(){

    }

    public static GameMap getRandomMap(){
        List<GameMap> allMaps = new ArrayList<>(maps);
        Random random = new Random();
        int randomIndex = random.nextInt(allMaps.size());
        return allMaps.get(randomIndex);
    }

    public static void addMap(String name, Location[] spawnLocations, Location[] crystalLocations, BoundingBox boundaries, Location spectatorLocation, World world){
        maps.add(new GameMap(name, spawnLocations, crystalLocations, boundaries, spectatorLocation, world));
    }
}

//static int for each map
//new folder: royale + int
//int represents number of instances of that map
//idk