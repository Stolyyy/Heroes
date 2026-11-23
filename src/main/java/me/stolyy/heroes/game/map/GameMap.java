package me.stolyy.heroes.game.map;

import me.stolyy.heroes.game.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;

public record GameMap(String name, ItemStack displayItem, String iconModelKey, Set<GameMode> supportedModes,
                      String worldFolderName, BoundingBox boundaries, Location spectatorLocation,
                      List<Location> spawnLocations, List<Location> crystalLocations, World world) {

    public GameMap withWorld(World newWorld) {
        return new GameMap(
            name, displayItem, iconModelKey, supportedModes, worldFolderName, boundaries,
            cloneLocation(spectatorLocation, newWorld),
            spawnLocations.stream().map(loc -> cloneLocation(loc, newWorld)).toList(),
            crystalLocations.stream().map(loc -> cloneLocation(loc, newWorld)).toList(),
            newWorld
        );
    }

    private static Location cloneLocation(Location loc, World world) {
        Location clone = loc.clone();
        clone.setWorld(world);
        return clone;
    }

    public Location getSpawnForTeam(int teamIndex, int playerIndex, List<Vector> offsets) {
        Location base = spawnLocations.get(teamIndex % spawnLocations.size()).clone();
        if (playerIndex >= 0 && playerIndex < offsets.size()) {
            Vector offset = offsets.get(playerIndex);
            base.add(offset);
        }
        return base;
    }

    /**
     * Spawn furthest from the nearest enemy (the player's own position when no enemy shares
     * their world). Only the reference point differs from
     * {@link #getFurthestSpawnFromLocation(Location)} — the search itself lives there.
     */
    public Location getFurthestSpawn(Player player, Set<Player> enemies) {
        Location referencePoint = player.getLocation();
        double nearestDistance = Double.MAX_VALUE;
        for (Player enemy : enemies) {
            if (enemy.getWorld() != player.getWorld()) continue;
            double distance = enemy.getLocation().distance(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                referencePoint = enemy.getLocation();
            }
        }
        return getFurthestSpawnFromLocation(referencePoint);
    }

    public Location getFurthestSpawnFromLocation(Location referencePoint) {
        if (spawnLocations.isEmpty()) return spectatorLocation.clone();
        double furthestDistance = -1;
        Location furthestSpawn = spawnLocations.get(0);
        for (Location spawn : spawnLocations) {
            double distance = referencePoint.distance(spawn);
            if (distance > furthestDistance) {
                furthestDistance = distance;
                furthestSpawn = spawn;
            }
        }
        // Clone, matching getSpawnForTeam: callers add offsets to the result, and returning
        // the record's own Location would let them mutate the shared map data in place.
        return furthestSpawn.clone();
    }
}
