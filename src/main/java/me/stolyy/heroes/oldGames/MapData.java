package me.stolyy.heroes.oldGames;

import me.stolyy.heroes.Game.GameEnums;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public
class MapData {
    private final String name;
    private final BoundingBox boundingBox;
    private final Map<GameEnums.GameTeam, List<Location>> spawnPoints;
    private final Location spectatorSpawn;

    public MapData(String name, BoundingBox boundingBox, Map<GameEnums.GameTeam, List<Location>> spawnPoints, Location spectatorSpawn) {
        this.name = name;
        this.boundingBox = boundingBox;
        this.spawnPoints = new EnumMap<>(spawnPoints);
        this.spectatorSpawn = spectatorSpawn;
    }

    public String getName() {
        return name;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public Map<GameEnums.GameTeam, List<Location>> getTeamSpawnPoints() {
        return new EnumMap<>(spawnPoints);
    }

    public Location getRandomSpawnPoint(GameEnums.GameTeam team) {
        List<Location> teamSpawns = spawnPoints.get(team);
        if (teamSpawns != null && !teamSpawns.isEmpty()) {
            return teamSpawns.get(new Random().nextInt(teamSpawns.size()));
        }
        return spectatorSpawn; // Fallback to spectator spawn if no team spawn is available
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public Location getWaitingArea() {
        // You might want to define a specific waiting area for each map
        // For now, we'll use the spectator spawn as the waiting area
        return spectatorSpawn;
    }
}
