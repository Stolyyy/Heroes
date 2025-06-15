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
import java.util.stream.Collectors;

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

    public static GameMap getRandomMapInAny(Set<GameEnums.GameMode> modes){
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

    public static GameMap getRandomMapInAll(Set<GameEnums.GameMode> modes){
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

    public static Set<GameMap> getMapsForMode(GameEnums.GameMode mode) {
        return Collections.unmodifiableSet(mapPools.getOrDefault(mode, Collections.emptySet()));
    }

    public static World loadTemplateForEditing(String mapName) {
        GameMap template = templateMaps.stream().filter(m -> m.name().equalsIgnoreCase(mapName)).findFirst().orElse(null);
        if (template == null) return null;

        World world = Bukkit.getWorld(template.worldFolderName());
        if (world == null) {
            world = new WorldCreator("templates/" + template.worldFolderName()).createWorld();
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

    public static boolean updateMapConfig(Player player, String mapName, String[] commandArgs) {
        File mapFile = new File(mapConfigFolder, mapName + ".json");
        if (!mapFile.exists()) {
            player.sendMessage("Error: Map config file not found for '" + mapName + "'.");
            return false;
        }

        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data;
            try (Reader reader = new FileReader(mapFile)) {
                data = gson.fromJson(reader, type);
            }

            String property = commandArgs[2].toLowerCase();

            switch (property) {
                case "spectatorlocation":
                case "spawnlocation":
                case "crystallocation": {
                    // handles all location-based settings
                    Map<String, Double> newLocMap = Map.of("x", player.getLocation().getX(), "y", player.getLocation().getY(), "z", player.getLocation().getZ());
                    int index = (commandArgs.length > 3) ? Integer.parseInt(commandArgs[3]) : -1;

                    if (property.equals("spectatorlocation")) {
                        data.put("spectatorLocation", newLocMap);
                    } else { // Handles spawn and crystal locations
                        List<Map<String, Double>> locs = (List<Map<String, Double>>) data.get(property + "s"); // "spawnlocations" or "crystallocations"
                        if (index >= 0 && index < locs.size()) {
                            locs.set(index, newLocMap);
                        } else {
                            player.sendMessage("Error: Invalid index '" + index + "'.");
                            return false;
                        }
                    }
                    break;
                }
                case "boundary1":
                    boundarySelections.put(player.getUniqueId(), player.getLocation());
                    player.sendMessage("Set boundary corner 1 for '" + mapName + "'. Use '/mapadmin set " + mapName + " boundary2' to finish.");
                    return true;
                case "boundary2": {
                    Location corner1 = boundarySelections.remove(player.getUniqueId());
                    if (corner1 == null) {
                        player.sendMessage("Error: You must set boundary corner 1 first.");
                        return false;
                    }
                    BoundingBox newBounds = BoundingBox.of(corner1, player.getLocation());
                    Map<String, Object> boundsMap = new HashMap<>();
                    boundsMap.put("min", Map.of("x", newBounds.getMinX(), "y", newBounds.getMinY(), "z", newBounds.getMinZ()));
                    boundsMap.put("max", Map.of("x", newBounds.getMaxX(), "y", newBounds.getMaxY(), "z", newBounds.getMaxZ()));
                    data.put("boundaries", boundsMap);
                    break;
                }

                case "displayitem": {
                    if (commandArgs.length < 4) {
                        player.sendMessage("Usage: /mapadmin set <mapName> displayitem <MATERIAL_NAME>");
                        return false;
                    }
                    String materialName = commandArgs[3].toUpperCase();
                    try {
                        Material.valueOf(materialName);
                        data.put("displayItem", materialName);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("Error: Invalid material name '" + materialName + "'.");
                        return false;
                    }
                    break;
                }
                case "gamemode": {
                    if (commandArgs.length < 5) {
                        player.sendMessage("Usage: /mapadmin set <mapName> gamemode <add|remove> <mode>");
                        return false;
                    }
                    String action = commandArgs[3].toLowerCase();
                    String modeName = commandArgs[4].toUpperCase();
                    List<String> supportedModes = (List<String>) data.get("supportedGameModes");

                    try {
                        GameEnums.GameMode.valueOf(modeName);
                        if (action.equals("add")) {
                            if (!supportedModes.contains(modeName)) {
                                supportedModes.add(modeName);
                            } else {
                                player.sendMessage("Info: Map already supports gamemode '" + modeName + "'.");
                                return true;
                            }
                        } else if (action.equals("remove")) {
                            if (!supportedModes.remove(modeName)) {
                                player.sendMessage("Info: Map does not support gamemode '" + modeName + "'.");
                                return true;
                            }
                        } else {
                            player.sendMessage("Error: Unknown action '" + action + "'. Use 'add' or 'remove'.");
                            return false;
                        }
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("Error: Invalid gamemode '" + modeName + "'.");
                        return false;
                    }
                    break;
                }
                default:
                    player.sendMessage("Error: Unknown property '" + property + "'.");
                    return false;
            }

            try (Writer writer = new FileWriter(mapFile)) {
                gson.toJson(data, writer);
            }

            loadMapsFromConfig();
            return true;

        } catch (IOException | ClassCastException e) {
            player.sendMessage("An error occurred while updating the map config.");
            e.printStackTrace();
            return false;
        }
    }

    public static String getMapProperty(String mapName, String property, int index) {
        GameMap template = templateMaps.stream()
                .filter(m -> m.name().equalsIgnoreCase(mapName))
                .findFirst()
                .orElse(null);

        if (template == null) {
            return "Error: Map not found.";
        }

        switch (property.toLowerCase()) {
            case "spectatorlocation":
                return formatLocation(template.spectatorLocation());
            case "spawnlocation":
                if (index >= 0 && index < template.spawnLocations().length) {
                    return formatLocation(template.spawnLocations()[index]);
                } else {
                    return "Error: Invalid spawn index.";
                }
            case "crystallocation":
                if (index >= 0 && index < template.crystalLocations().length) {
                    return formatLocation(template.crystalLocations()[index]);
                } else {
                    return "Error: Invalid crystal index.";
                }
            case "boundaries":
                if (template.boundaries() == null) return "N/A";
                return "Min: " + formatLocation(template.boundaries().getMin().toLocation(null)) +
                        ", Max: " + formatLocation(template.boundaries().getMax().toLocation(null));

            case "displayitem":
                if (template.displayItem() == null) return "N/A";
                return template.displayItem().getType().name();

            case "gamemode":
            case "gamemodes":
                if (template.supportedModes() == null || template.supportedModes().isEmpty()) return "None";
                return template.supportedModes().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));

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
                    plugin.getLogger().info("Loaded map template: " + map.name());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Could not parse map configuration: " + file.getName());
                e.printStackTrace();
            }
        }

        for (GameMap map : templateMaps) {
            for (GameEnums.GameMode mode : map.supportedModes()) {
                mapPools.get(mode).add(map);
            }
        }
    }

    public static List<String> getTemplateMapNames() {
        List<String> names = new ArrayList<>();
        for (GameMap map : templateMaps) {
            names.add(map.name());
        }
        return names;
    }

    public static GameMap createWorld(GameMap template){
        String worldName = template.worldFolderName() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File templateWorldFolder = new File(templatesFolder, template.worldFolderName());
        File newWorldFolder = new File(activeWorldsFolder, worldName);

        if (!templateWorldFolder.exists()) {
            plugin.getLogger().severe("Cannot create world. Template folder not found: " + templateWorldFolder.getPath());
            return null;
        }

        try {
            copyDirectory(templateWorldFolder, newWorldFolder);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy template world '" + template.worldFolderName() + "'.");
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
        World world = map.world();
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