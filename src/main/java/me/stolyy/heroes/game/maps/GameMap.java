package me.stolyy.heroes.game.maps;

import me.stolyy.heroes.game.minigame.Game;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.Set;

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

    public Location getFurthestSpawn(Player player, Set<Player> enemies){
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player enemy : enemies) {
            if (enemy.getWorld() == player.getWorld()) {
                double distance = enemy.getLocation().distance(player.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = enemy;
                }
            }
        }
        //respawn player at furthest spawn point from enemy location (or player location if no enemy)
        double furthestDistance = 0;
        Location referencePoint = player.getLocation();
        Location furthestSpawn = getSpawnLocations()[0];
        if (nearestPlayer != null) referencePoint = nearestPlayer.getLocation();

        for (Location spawn : getSpawnLocations()) {
            double distance = referencePoint.distance(spawn);
            if (distance > furthestDistance){
                furthestDistance = distance;
                furthestSpawn = spawn;
            }
        }
        return furthestSpawn;
    }

    public String getName() {return name;}
    public World getWorld() {return world;}
    public Location[] getSpawnLocations() {return spawnLocations;}
    public Location[] getCrystalLocations() {return crystalLocations;}
    public BoundingBox getBoundaries() {return boundaries;}
    public Location getSpectatorLocation() {return spectatorLocation;}
}
