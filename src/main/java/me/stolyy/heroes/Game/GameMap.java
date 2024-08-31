package me.stolyy.heroes.Game;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

public class GameMap {
    final String name;
    final Location[] spawnLocations;
    final Location[] crystalLocations;
    final BoundingBox boundaries;
    final Location spectatorLocation;

    public GameMap(String name, Location[] spawnLocations, Location[] crystalLocations, BoundingBox boundaries, Location spectatorLocation) {
        this.name = name;
        this.spawnLocations = spawnLocations;
        this.crystalLocations = crystalLocations;
        this.boundaries = boundaries;
        this.spectatorLocation = spectatorLocation;
    }

    public String getName() {return name;}
    public Location[] getSpawnLocations() {return spawnLocations;}
    public Location[] getCrystalLocations() {return crystalLocations;}
    public BoundingBox getBoundaries() {return boundaries;}
    public Location getSpectatorLocation() {return spectatorLocation;}
}
