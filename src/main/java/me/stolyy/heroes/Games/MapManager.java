package me.stolyy.heroes.Games;

import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.Heroes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.*;

public class MapManager {
    private final List<MapData> maps;
    private final World gameWorld;

    public MapManager() {
        this.maps = new ArrayList<>();
        this.gameWorld = Heroes.getInstance().getServer().getWorld("world"); // Assuming "world" is your game world
        initializeMaps();
    }

    private void initializeMaps() {
        // Define maps here
        addMap("Royale", new BoundingBox(251, 50, 102, 366, -65, 232),
                createTeamSpawnPoints(
                        new Location(gameWorld, 317, -13, 183),
                        new Location(gameWorld, 299, -13, 183),
                        new Location(gameWorld, 299, -13, 150),
                        new Location(gameWorld, 317, -13, 150)
                ),
                new Location(gameWorld, 308, 0, 167));

        addMap("Burger", new BoundingBox(260, 66, -52, 371, -65, -163),
                createTeamSpawnPoints(
                        new Location(gameWorld, 315, 0, -130),
                        new Location(gameWorld, 333, 5, -112),
                        new Location(gameWorld, 315, -4, -90),
                        new Location(gameWorld, 296, -5, -112)
                ),
                new Location(gameWorld, 315, 10, -107));

        addMap("PENIS", new BoundingBox(250, 50, -315, 353, -65, -416),
                createTeamSpawnPoints(
                        new Location(gameWorld, 304, -4, -354),
                        new Location(gameWorld, 290, -17, -370),
                        new Location(gameWorld, 314, -15, -375),
                        new Location(gameWorld, 320, -15, -358)
                ),
                new Location(gameWorld, 302, 0, -365));
    }

    private Map<GameEnums.GameTeam, List<Location>> createTeamSpawnPoints(Location... locations) {
        Map<GameEnums.GameTeam, List<Location>> spawnPoints = new EnumMap<>(GameEnums.GameTeam.class);
        GameEnums.GameTeam[] teams = GameEnums.GameTeam.values();
        for (int i = 0; i < teams.length && i < locations.length; i++) {
            spawnPoints.put(teams[i], Collections.singletonList(locations[i]));
        }
        return spawnPoints;
    }

    private void addMap(String name, BoundingBox boundingBox, Map<GameEnums.GameTeam, List<Location>> spawnPoints, Location spectatorSpawn) {
        maps.add(new MapData(name, boundingBox, spawnPoints, spectatorSpawn));
    }

    public MapData getRandomMap() {
        if (maps.isEmpty()) {
            Heroes.getInstance().getLogger().severe("No maps available! The maps list is empty.");
            return null;
        }

        try {
            MapData selectedMap = maps.get(new Random().nextInt(maps.size()));
            Heroes.getInstance().getLogger().info("Selected random map: " + selectedMap.getName());
            return selectedMap;
        } catch (IllegalArgumentException e) {
            Heroes.getInstance().getLogger().severe("Failed to select a random map. Maps size: " + maps.size());
            return null;
        }
    }

    public List<MapData> getAllMaps() {
        return new ArrayList<>(maps);
    }
}
