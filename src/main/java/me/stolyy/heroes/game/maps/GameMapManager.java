package me.stolyy.heroes.game.maps;

import me.stolyy.heroes.Heroes;
import org.bukkit.*;
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
    private static final File templateFolder = new File(serverWorldFolder, "templates");
    private static final Random random = new Random();

    public static void initializeMaps(){
        addMap("Burgers", new Location[]{
                new Location(null, 1, 0, -22),
                new Location(null,  17, 5, -3),
                new Location(null,  0, -4, 19),
                new Location(null,  -19, -5, -2)},
                new Location[]{
                        new Location(null, -12, 6, -12),
                        new Location(null,  1, 3, 9),
                        new Location(null,  0, 8, 0),
                        new Location(null,  2, 9, -16)},
                new BoundingBox(55, -64, 55, -54, 63, -54),
                new Location(null, 1, 10, -9));
        addMap("Dystopia", new Location[]{
                        new Location(null, -12, 11, 8),
                        new Location(null,  -14, 2, -10),
                        new Location(null,  12, 2, -14),
                        new Location(null,  10, 10, 16)},
                new Location[]{
                        new Location(null, -19, 2, 17),
                        new Location(null,  7, 8, 1),
                        new Location(null,  -6, -4, 9),
                        new Location(null,  -2, 1, -3)},
                new BoundingBox(50, -64, -52, -56, 67, 60),
                new Location(null, -6, 17, 8));
        addMap("Royale", new Location[]{
                        new Location(null, 9, -13, -16),
                        new Location(null,  9, -13, 16),
                        new Location(null,  -8, -13, 16),
                        new Location(null,  -8, -13, -16)},
                new Location[]{
                        new Location(null, 1, -19, 0),
                        new Location(null,  0, -20, -25),
                        new Location(null,  1, -20, 25),
                        new Location(null,  0, -20, -25)},
                new BoundingBox(57, 48, 64, -56, -64, -64),
                new Location(null, 1, -10, 0));
        addMap("Penis", new Location[]{
                        new Location(null, 5, -4, 12),
                        new Location(null,  -19, -9, 14),
                        new Location(null,  0, -17, -18),
                        new Location(null,  15, -15, 2)},
                new Location[]{
                        new Location(null, 11, -6, -5),
                        new Location(null,  0, -15, 13),
                        new Location(null,  -10, -9, 6),
                        new Location(null,  -8, -15, -8)},
                new BoundingBox(-57, 49, -49, 44, -64, 50),
                new Location(null, 1, -8, 6));
    }

    public static GameMap createWorld(GameMap map) {
        File templateMapFolder = new File(templateFolder, map.name);
        String newWorldName = map.name + "_" + UUID.randomUUID();
        File newWorldFolder = new File(serverWorldFolder, newWorldName);

        try {
            copyWorld(templateMapFolder, newWorldFolder);
        } catch (IOException e) {
            Heroes.getInstance().getLogger().severe("Failed to copy world: " + e.getMessage());
            return null;
        }

        WorldCreator creator = new WorldCreator(newWorldName);
        World world = creator.createWorld();

        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, true);
            world.setFullTime(10000L);
        }


        Location[] updatedSpawnLocations = Arrays.stream(map.spawnLocations)
                .map(loc -> new Location(world, loc.getX(), loc.getY(), loc.getZ()))
                .toArray(Location[]::new);

        Location[] updatedCrystalLocations = Arrays.stream(map.crystalLocations)
                .map(loc -> new Location(world, loc.getX(), loc.getY(), loc.getZ()))
                .toArray(Location[]::new);

        Location updatedSpectatorLocation = new Location(world, map.spectatorLocation.getX(), map.spectatorLocation.getY(), map.spectatorLocation.getZ());

        GameMap gameMap = new GameMap(map.name, updatedSpawnLocations, updatedCrystalLocations,
                map.boundaries, updatedSpectatorLocation, world);

        maps.add(gameMap);
        return gameMap;
    }

    public static void deleteWorld(GameMap map) {
        World world = map.getWorld();
        if (world != null && Bukkit.getWorld(world.getName()) != null) {
            Bukkit.unloadWorld(world, false);
        }
        File worldFolder = world.getWorldFolder();


        if (worldFolder.exists()) {
            File[] files = worldFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    delete(file);
                }
            }
            worldFolder.delete();
        }
        maps.remove(map);
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File child : contents) {
                    delete(child);
                }
            }
        }
        file.delete();
    }

    private static void copyWorld(File source, File target) throws IOException {
        if (!target.exists()) {
            target.mkdirs();
        }

        Files.walk(source.toPath()).forEach(sourcePath -> {
            try {
                Path targetPath = target.toPath().resolve(source.toPath().relativize(sourcePath));
                String fileName = sourcePath.getFileName().toString();
                if (fileName.equalsIgnoreCase("uid.dat") || fileName.equalsIgnoreCase("session.lock")) {
                    return;
                }
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

    public static void addMap(String name, Location[] spawnLocations, Location[] crystalLocations, BoundingBox boundaries, Location spectatorLocation){
        templateMaps.add(new GameMap(name, spawnLocations, crystalLocations, boundaries, spectatorLocation, null));
    }
}