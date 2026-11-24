package me.stolyy.heroes.game.map;

import me.stolyy.heroes.game.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Map definition and optional live match world. Template records stored by {@link GameMapManager}
 * carry {@code world == null}; per-match instances minted by {@link WorldInstances#createWorld}
 * bind a cloned world onto {@code world} and every location.
 */
public record GameMap(String name, ItemStack displayItem, @Nullable String iconModelKey, Set<GameMode> supportedModes,
                      String worldFolderName, BoundingBox boundaries, Location spectatorLocation,
                      List<Location> spawnLocations, List<Location> crystalLocations, @Nullable World world) {

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
        return applyOffset(base, playerIndex, offsets);
    }

    /** Applies the indexed roster spawn offset to a cloned spawn point, when present. */
    public static Location applyOffset(Location base, int playerIndex, List<Vector> offsets) {
        if (playerIndex >= 0 && playerIndex < offsets.size()) {
            base.add(offsets.get(playerIndex));
        }
        return base;
    }

    public Location getFurthestSpawnFromLocation(Location referencePoint) {
        if (spawnLocations.isEmpty()) {
            throw new IllegalStateException("Map '" + name + "' has no spawn locations");
        }
        double furthestDistance = -1;
        Location furthestSpawn = spawnLocations.get(0);
        for (Location spawn : spawnLocations) {
            double distance = referencePoint.distance(spawn);
            if (distance > furthestDistance) {
                furthestDistance = distance;
                furthestSpawn = spawn;
            }
        }
        // Clone so callers adding roster offsets never mutate the shared map record.
        return furthestSpawn.clone();
    }
}
