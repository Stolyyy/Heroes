package me.stolyy.heroes.Game;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class GameMapManager {
    static Set<GameMap> maps = new HashSet<>();
    public static Set<GameMap> templateMaps = new HashSet<>();
    private static final File serverWorldFolder = new File(Bukkit.getWorldContainer().getAbsolutePath());
    private static final File mapsFolder = new File(serverWorldFolder, "maps");
    private static final Random random = new Random();

    public static void initializeMaps(){
        World world = Heroes.getInstance().getServer().getWorld("Burgers");
        addMap("Burgers", new Location[]{
                new Location(world, 315, 0, -130),
                new Location(world,  333, 5, -112),
                new Location(world,  315, -4, -90),
                new Location(world,  296, -5, -112)},
                new Location[]{
                        new Location(world, 315, 0, -130),
                        new Location(world,  333, 5, -112),
                        new Location(world,  315, -4, -90),
                        new Location(Heroes.getInstance().getServer().getWorld("Burgers"),  296, -5, -112)},
                new BoundingBox(260, 66, -52, 371, -65, -163),
                new Location(world, 315, 10, -107),
                world);
    }

    public static GameMap createWorld(GameMap map) throws IOException {
        File templateMapFolder = new File(mapsFolder, map.name);
        String newWorldName = map.name + UUID.randomUUID();
        File newWorldFolder = new File(serverWorldFolder, newWorldName);

        try {
            copyWorld(templateMapFolder, newWorldFolder);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to copy world: " + e.getMessage());
            return null;
        }

        WorldCreator creator = new WorldCreator(newWorldName);
        World world = creator.createWorld();

        GameMap gameMap = new GameMap(map.name, map.spawnLocations, map.crystalLocations,
                map.boundaries, map.spectatorLocation, world);

        maps.add(gameMap);
        return gameMap;
    }

    public static void deleteWorld(File worldFolder) {
        if (worldFolder.exists()) {
            File[] files = worldFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteWorld(file);
                }
            }
            worldFolder.delete();
        }
    }

    private static void copyWorld(File source, File target) throws IOException {
        if (!target.exists()) {
            target.mkdirs();
        }
        Files.walk(source.toPath()).forEach(sourcePath -> {
            try {
                Path targetPath = target.toPath().resolve(source.toPath().relativize(sourcePath));
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static GameMap getRandomMap() {
        if (templateMaps.isEmpty()) {
            return null;
        }
        int index = random.nextInt(templateMaps.size());
        return templateMaps.stream().skip(index).findFirst().orElse(null);
    }

    public static void addMap(String name, Location[] spawnLocations, Location[] crystalLocations, BoundingBox boundaries, Location spectatorLocation, World world){
        maps.add(new GameMap(name, spawnLocations, crystalLocations, boundaries, spectatorLocation, world));
    }
}

//static int for each map
//new folder: royale + int
//int represents number of instances of that map
//idk