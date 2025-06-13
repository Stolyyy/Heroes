package me.stolyy.heroes.game.maps;

import me.stolyy.heroes.game.minigame.GameEnums;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Set;

public class GameMap {
    private final String name;
    private final ItemStack displayItem;
    private final Set<GameEnums.GameMode> supportedModes;
    private final String worldFolderName;
    private final BoundingBox boundaries;
    private final Location spectatorLocation;
    private final Location[] spawnLocations;
    private final Location[] crystalLocations;
    private final World world;

    public GameMap(String name, ItemStack displayItem, Set<GameEnums.GameMode> supportedModes, String worldFolderName, BoundingBox boundaries, Location spectatorLocation, Location[] spawnLocations, Location[] crystalLocations, World world) {
        this.name = name;
        this.displayItem = displayItem;
        this.supportedModes = supportedModes;
        this.worldFolderName = worldFolderName;
        this.boundaries = boundaries;
        this.spectatorLocation = spectatorLocation;
        this.spawnLocations = spawnLocations;
        this.crystalLocations = crystalLocations;
        this.world = world;
    }

    public GameMap createCopy(World world){
        Location updatedSpectatorLocation = spectatorLocation.clone();
        updatedSpectatorLocation.setWorld(world);

        Location[] updatedSpawnLocations = new Location[this.spawnLocations.length];
        for (int i = 0; i < this.spawnLocations.length; i++) {
            updatedSpawnLocations[i] = this.spawnLocations[i].clone();
            updatedSpawnLocations[i].setWorld(world);
        }

        Location[] updatedCrystalLocations = new Location[this.crystalLocations.length];
        for (int i = 0; i < this.crystalLocations.length; i++) {
            updatedCrystalLocations[i] = this.crystalLocations[i].clone();
            updatedCrystalLocations[i].setWorld(world);
        }

        return new GameMap(
                this.name,
                this.displayItem,
                this.supportedModes,
                this.worldFolderName,
                this.boundaries,
                updatedSpectatorLocation,
                updatedSpawnLocations,
                updatedCrystalLocations,
                world
        );
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
        Location furthestSpawn = spawnLocations[0];
        if (nearestPlayer != null) referencePoint = nearestPlayer.getLocation();

        for (Location spawn : spawnLocations) {
            double distance = referencePoint.distance(spawn);
            if (distance > furthestDistance){
                furthestDistance = distance;
                furthestSpawn = spawn;
            }
        }
        return furthestSpawn;
    }

    public String getName() {
        return name;
    }

    public ItemStack getDisplayItem() {
        return displayItem;
    }

    public Set<GameEnums.GameMode> getSupportedModes() {
        return supportedModes;
    }

    public String getWorldFolderName() {
        return worldFolderName;
    }

    public BoundingBox getBoundaries() {
        return boundaries;
    }

    public Location getSpectatorLocation() {
        return spectatorLocation;
    }

    public Location[] getSpawnLocations() {
        return spawnLocations;
    }

    public Location[] getCrystalLocations() {
        return crystalLocations;
    }

    public World getWorld() {
        return world;
    }
}
