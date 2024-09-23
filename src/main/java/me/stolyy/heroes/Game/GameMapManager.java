package me.stolyy.heroes.Game;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameMapManager {
    static Set<GameMap> maps;

    public static void initializeMaps(){

    }

    public static void createWorld(){

    }

    public static void deleteWorld(){

    }

    public static void addMap(String name, Location[] spawnLocations, Location[] crystalLocations, BoundingBox boundaries, Location spectatorLocation){
        maps.add(new GameMap(name, spawnLocations, crystalLocations, boundaries, spectatorLocation));
    }
}
