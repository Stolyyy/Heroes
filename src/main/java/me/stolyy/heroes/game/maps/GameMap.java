package me.stolyy.heroes.game.maps;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

public class GameMap {
    final String name;
    final Location[] spawnLocations;
    final Location[] crystalLocations;
    final BoundingBox boundaries;
    final Location spectatorLocation;
    final World world;

    public GameMap(String name, Location[] spawnLocations, Location[] crystalLocations, BoundingBox boundaries, Location spectatorLocation, World world) {
        this.name = name;
        this.spawnLocations = spawnLocations;
        this.crystalLocations = crystalLocations;
        this.boundaries = boundaries;
        this.spectatorLocation = spectatorLocation;
        this.world = world;
    }

    public String getName() {return name;}
    public World getWorld() {return world;}
    public Location[] getSpawnLocations() {return spawnLocations;}
    public Location[] getCrystalLocations() {return crystalLocations;}
    public BoundingBox getBoundaries() {return boundaries;}
    public Location getSpectatorLocation() {return spectatorLocation;}
}
