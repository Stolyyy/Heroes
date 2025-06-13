package me.stolyy.heroes.game.maps;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.minigame.GameEnums;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class GameMapManager {
    private static final Heroes plugin = Heroes.getInstance();
    private static final File mapConfigFolder = new File(plugin.getDataFolder(), "maps");
    private static final File templatesFolder = new File(plugin.getDataFolder(), "templates");
    private static final File activeWorldsFolder = new File(plugin.getDataFolder(), "active_worlds");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<GameMap> templateMaps = new LinkedHashSet<>();
    private static final Map<GameEnums.GameMode, Set<GameMap>> mapPools = new EnumMap<>(GameEnums.GameMode.class);

    private static final Map<UUID, Location> boundarySelections = new HashMap<>();

    public static GameMap getRandomMap(GameEnums.GameMode mode){
        Set<GameMap> pool = mapPools.get(mode);
        if (pool == null || pool.isEmpty()) return null;

        int index = new Random().nextInt(pool.size());
        return pool.stream().skip(index).findFirst().orElse(null);
    }

    public static GameMap getRandomMapUnion(Set<GameEnums.GameMode> modes){
        Set<GameMap> pool = new LinkedHashSet<>();
        for (GameEnums.GameMode mode : modes) {
            Set<GameMap> modePool = mapPools.get(mode);
            if (modePool != null && !modePool.isEmpty()) {
                pool.addAll(modePool);
            }
        }
        if (pool.isEmpty()) return null;

        int index = new Random().nextInt(pool.size());
        return pool.stream().skip(index).findFirst().orElse(null);
    }

    public static GameMap getRandomMapIntersect(Set<GameEnums.GameMode> modes){
        Set<GameMap> pool = new LinkedHashSet<>(templateMaps);
        for (GameEnums.GameMode mode : modes) {
            Set<GameMap> modePool = mapPools.get(mode);
            if (modePool != null && !modePool.isEmpty()) {
                pool.retainAll(modePool);
            }
        }
        if (pool.isEmpty()) return null;

        int index = new Random().nextInt(pool.size());
        return pool.stream().skip(index).findFirst().orElse(null);
    }

    public static World loadTemplateForEditing(String mapName) {
        GameMap template = templateMaps.stream().filter(m -> m.getName().equalsIgnoreCase(mapName)).findFirst().orElse(null);
        if (template == null) return null;

        World world = Bukkit.getWorld(template.getWorldFolderName());
        if (world == null) {
            world = new WorldCreator("templates/" + template.getWorldFolderName()).createWorld();
        }
        if(world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, true);
            world.setGameRule(GameRule.DISABLE_RAIDS, true);
        }


        return world;
    }

    public static void saveTemplateWorld(World world) {
        if (world.getName().startsWith("templates/")) {
            world.save();
            for(Player p : world.getPlayers()) {
                Heroes.teleportToLobby(p);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.unloadWorld(world, true), 20L);
        }
    }

    public static boolean updateMapConfig(Player player, String mapName, String property, Location location, int index) {
        File mapFile = new File(mapConfigFolder, mapName + ".json");
        if (!mapFile.exists()) return false;

        try {
            // Read existing data into a generic Map
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data;
            try (Reader reader = new FileReader(mapFile)) {
                data = gson.fromJson(reader, type);
            }

            Map<String, Double> newLocMap = Map.of(
                    "x", location.getX(),
                    "y", location.getY(),
                    "z", location.getZ()
            );

            switch (property.toLowerCase()) {
                case "spectatorlocation":
                    data.put("spectatorLocation", newLocMap);
                    break;
                case "spawnlocation":
                    List<Map<String, Double>> spawns = (List<Map<String, Double>>) data.get("spawnLocations");
                    if (index >= 0 && index < spawns.size()) {
                        spawns.set(index, newLocMap);
                    } else {
                        return false; // Invalid index
                    }
                    break;
                case "crystallocation":
                    List<Map<String, Double>> crystals = (List<Map<String, Double>>) data.get("crystalLocations");
                    if (index >= 0 && index < crystals.size()) {
                        crystals.set(index, newLocMap);
                    } else {
                        return false; // Invalid index
                    }
                    break;
                case "boundary1":
                    boundarySelections.put(player.getUniqueId(), location);
                    player.sendMessage("Set boundary corner 1. Use '/mapadmin set " + mapName + " boundary2' to finish.");
                    return true;
                case "boundary2":
                    Location corner1 = boundarySelections.remove(player.getUniqueId());
                    if(corner1 == null) {
                        player.sendMessage("You must set boundary corner 1 first.");
                        return false;
                    }
                    BoundingBox newBounds = BoundingBox.of(corner1, location);
                    Map<String, Object> boundsMap = new HashMap<>();
                    boundsMap.put("min", Map.of("x", newBounds.getMinX(), "y", newBounds.getMinY(), "z", newBounds.getMinZ()));
                    boundsMap.put("max", Map.of("x", newBounds.getMaxX(), "y", newBounds.getMaxY(), "z", newBounds.getMaxZ()));
                    data.put("boundaries", boundsMap);
                    break;
                default:
                    return false; // Unknown property
            }

            try (Writer writer = new FileWriter(mapFile)) {
                gson.toJson(data, writer);
            }

            loadMapsFromConfig();
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getMapProperty(String mapName, String property, int index) {
        GameMap template = templateMaps.stream()
                .filter(m -> m.getName().equalsIgnoreCase(mapName))
                .findFirst()
                .orElse(null);

        if (template == null) {
            return "Error: Map not found.";
        }

        switch (property.toLowerCase()) {
            case "spectatorlocation":
                return formatLocation(template.getSpectatorLocation());
            case "spawnlocation":
                if (index >= 0 && index < template.getSpawnLocations().length) {
                    return formatLocation(template.getSpawnLocations()[index]);
                } else {
                    return "Error: Invalid spawn index.";
                }
            case "crystallocation":
                if (index >= 0 && index < template.getCrystalLocations().length) {
                    return formatLocation(template.getCrystalLocations()[index]);
                } else {
                    return "Error: Invalid crystal index.";
                }
            case "boundaries":
                return "Min: " + formatLocation(template.getBoundaries().getMin().toLocation(null)) +
                        ", Max: " + formatLocation(template.getBoundaries().getMax().toLocation(null));
            default:
                return "Error: Unknown property.";
        }
    }

    public static void loadMapsFromConfig(){
        if (!mapConfigFolder.exists()) mapConfigFolder.mkdirs();
        if (!templatesFolder.exists()) templatesFolder.mkdirs();
        if (!activeWorldsFolder.exists()) activeWorldsFolder.mkdirs();

        templateMaps.clear();
        mapPools.clear();
        for (GameEnums.GameMode mode : GameEnums.GameMode.values()) {
            mapPools.put(mode, new LinkedHashSet<>());
        }

        File[] jsonFiles = mapConfigFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null) {
            plugin.getLogger().warning("No map configurations found in the maps folder.");
            return;
        }

        for (File file : jsonFiles) {
            try {
                GameMap map = parseMapFromJson(file);
                if (map != null) {
                    templateMaps.add(map);
                    plugin.getLogger().info("Loaded map template: " + map.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Could not parse map configuration: " + file.getName());
                e.printStackTrace();
            }
        }

        for (GameMap map : templateMaps) {
            for (GameEnums.GameMode mode : map.getSupportedModes()) {
                mapPools.get(mode).add(map);
            }
        }
    }

    public static List<String> getTemplateMapNames() {
        List<String> names = new ArrayList<>();
        for (GameMap map : templateMaps) {
            names.add(map.getName());
        }
        return names;
    }

    public static GameMap createWorld(GameMap template){
        String worldName = template.getWorldFolderName() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File templateWorldFolder = new File(templatesFolder, template.getWorldFolderName());
        File newWorldFolder = new File(activeWorldsFolder, worldName);

        if (!templateWorldFolder.exists()) {
            plugin.getLogger().severe("Cannot create world. Template folder not found: " + templateWorldFolder.getPath());
            return null;
        }

        try {
            copyDirectory(templateWorldFolder, newWorldFolder);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy template world '" + template.getWorldFolderName() + "'.");
            e.printStackTrace();
            return null;
        }

        World world = new WorldCreator(activeWorldsFolder.getName() + "/" + newWorldFolder.getName()).createWorld();
        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, true);
            world.setGameRule(GameRule.DISABLE_RAIDS, true);
            world.setFullTime(10000L);
        }

        return template.createCopy(world);
    }

    public static void deleteWorld(GameMap map){
        World world = map.getWorld();
        if (world == null) return;

        File worldFolder = world.getWorldFolder();

        if (Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().info("Successfully unloaded world: " + world.getName());
        } else {
            plugin.getLogger().severe("Could not unload world: " + world.getName());
            return;
        }

        try {
            deleteDirectory(worldFolder);
            plugin.getLogger().info("Successfully deleted world folder: " + worldFolder.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to delete world folder: " + worldFolder.getName());
            e.printStackTrace();
        }
    }

    private static GameMap parseMapFromJson(File jsonFile) throws FileNotFoundException {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(new FileReader(jsonFile), type);

        String name = (String) data.get("name");
        String displayItemStr = (String) data.get("displayItem");
        ItemStack displayItem = new ItemStack(Material.valueOf(displayItemStr.toUpperCase()));

        List<String> modesList = (List<String>) data.get("supportedGameModes");
        Set<GameEnums.GameMode> supportedModes = new HashSet<>();
        for (String mode : modesList) {
            supportedModes.add(GameEnums.GameMode.valueOf(mode.toUpperCase()));
        }

        String worldFolderName = (String) data.get("worldFolder");

        // Manually parse nested objects
        Map<String, Map<String, Double>> boundsMap = (Map<String, Map<String, Double>>) data.get("boundaries");
        BoundingBox boundaries = BoundingBox.of(
                parseLocation(boundsMap.get("min")),
                parseLocation(boundsMap.get("max"))
        );

        Map<String, Double> specLocMap = (Map<String, Double>) data.get("spectatorLocation");
        Location spectatorLocation = parseLocation(specLocMap);

        List<Map<String, Double>> spawnLocsList = (List<Map<String, Double>>) data.get("spawnLocations");
        Location[] spawnLocations = new Location[spawnLocsList.size()];
        for (int i = 0; i < spawnLocsList.size(); i++) {
            spawnLocations[i] = parseLocation(spawnLocsList.get(i));
        }

        List<Map<String, Double>> crystalLocsList = (List<Map<String, Double>>) data.get("crystalLocations");
        Location[] crystalLocations = new Location[crystalLocsList.size()];
        for (int i = 0; i < crystalLocsList.size(); i++) {
            crystalLocations[i] = parseLocation(crystalLocsList.get(i));
        }

        // The world is null for templates
        return new GameMap(name, displayItem, supportedModes, worldFolderName, boundaries, spectatorLocation, spawnLocations, crystalLocations, null);
    }

    private static Location parseLocation(Map<String, Double> locMap) {
        return new Location(null, locMap.get("x"), locMap.get("y"), locMap.get("z"));
    }

    private static String formatLocation(Location loc) {
        if (loc == null) return "N/A";
        return String.format("X: %.1f, Y: %.1f, Z: %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    private static void copyDirectory(File source, File target) throws IOException {
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
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void deleteDirectory(File directory) throws IOException {
        Files.walk(directory.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}