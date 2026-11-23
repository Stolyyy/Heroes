package me.stolyy.heroes.game.map;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.game.GameMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class GameMapManager {
    private static Heroes plugin;
    private static File mapConfigFolder;
    private static File templatesFolder;
    private static File activeWorldsFolder;

    private static final Set<GameMap> templateMaps = new LinkedHashSet<>();
    private static final Map<GameMode, Set<GameMap>> mapPools = new EnumMap<>(GameMode.class);
    private static final Map<UUID, Location> boundarySelections = new HashMap<>();

    public static void initialize() {
        plugin = Heroes.getInstance();
        mapConfigFolder = new File(plugin.getDataFolder(), "maps");
        templatesFolder = new File(plugin.getServer().getWorldContainer(), "templates");
        activeWorldsFolder = new File(plugin.getServer().getWorldContainer(), "active_worlds");

        if (!mapConfigFolder.exists()) mapConfigFolder.mkdirs();
        if (!templatesFolder.exists()) templatesFolder.mkdirs();
        if (!activeWorldsFolder.exists()) activeWorldsFolder.mkdirs();

        purgeOrphanedInstances();
        loadMapsFromConfig();
    }

    /**
     * Deletes every per-game instance folder left in {@code active_worlds/} by a previous run.
     * Instances are minted per match and deleted at game end, so anything on disk at startup is an
     * orphan from a crash or a kill -9. If another plugin has already loaded one of them as a world
     * (Multiverse auto-load), it is unloaded first so the folder delete does not hit locked files.
     */
    private static void purgeOrphanedInstances() {
        File[] folders = activeWorldsFolder.listFiles(File::isDirectory);
        if (folders == null || folders.length == 0) return;

        int deleted = 0;
        for (File folder : folders) {
            for (World loaded : new ArrayList<>(Bukkit.getWorlds())) {
                if (loaded.getWorldFolder().getAbsoluteFile().equals(folder.getAbsoluteFile())) {
                    Bukkit.unloadWorld(loaded, false);
                }
            }
            try {
                deleteDirectory(folder);
                deleted++;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to delete orphaned instance world: " + folder.getName());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info("Deleted " + deleted + " orphaned instance world(s) from " + activeWorldsFolder.getName());
    }

    public static GameMap getRandomMap(GameMode mode) {
        Set<GameMap> pool = mapPools.get(mode);
        if (pool == null || pool.isEmpty()) return null;

        List<GameMap> list = new ArrayList<>(pool);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public static GameMap getRandomMapInAny(Set<GameMode> modes) {
        Set<GameMap> pool = new LinkedHashSet<>();
        for (GameMode mode : modes) {
            Set<GameMap> modePool = mapPools.get(mode);
            if (modePool != null && !modePool.isEmpty()) {
                pool.addAll(modePool);
            }
        }
        if (pool.isEmpty()) return null;

        List<GameMap> list = new ArrayList<>(pool);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public static GameMap getRandomMapInAll(Set<GameMode> modes) {
        Set<GameMap> pool = new LinkedHashSet<>(templateMaps);
        for (GameMode mode : modes) {
            Set<GameMap> modePool = mapPools.get(mode);
            if (modePool != null && !modePool.isEmpty()) {
                pool.retainAll(modePool);
            }
        }
        if (pool.isEmpty()) return null;

        List<GameMap> list = new ArrayList<>(pool);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public static Set<GameMap> getMapsForMode(GameMode mode) {
        return Collections.unmodifiableSet(mapPools.getOrDefault(mode, Collections.emptySet()));
    }

    /** Resolves a preset {@link MapPool} selector against the loaded per-mode pools. */
    public static Set<GameMap> getMapsForPool(MapPool pool) {
        return switch (pool) {
            case ONE_V_ONE -> getMapsForMode(GameMode.ONE_V_ONE);
            case TWO_V_TWO -> getMapsForMode(GameMode.TWO_V_TWO);
            case ANY -> {
                Set<GameMap> union = new LinkedHashSet<>(getMapsForMode(GameMode.ONE_V_ONE));
                union.addAll(getMapsForMode(GameMode.TWO_V_TWO));
                yield Collections.unmodifiableSet(union);
            }
            case BOTH -> {
                Set<GameMap> intersection = new LinkedHashSet<>(getMapsForMode(GameMode.ONE_V_ONE));
                intersection.retainAll(getMapsForMode(GameMode.TWO_V_TWO));
                yield Collections.unmodifiableSet(intersection);
            }
            case ALL -> Collections.unmodifiableSet(templateMaps);
        };
    }

    public static GameMap getRandomMapForPool(MapPool pool) {
        Set<GameMap> maps = getMapsForPool(pool);
        if (maps.isEmpty()) return null;
        List<GameMap> list = new ArrayList<>(maps);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** Looks up a template map by name (case-insensitive). Returns null if not found. */
    public static GameMap findMapByName(String name) {
        if (name == null) return null;
        return templateMaps.stream()
                .filter(m -> m.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /** Returns all known template map names, sorted alphabetically. Useful for tab completion. */
    public static List<String> getAllMapNames() {
        return templateMaps.stream()
                .map(GameMap::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static World loadTemplateForEditing(String mapName) {
        GameMap template = templateMaps.stream()
            .filter(m -> m.name().equalsIgnoreCase(mapName))
            .findFirst()
            .orElse(null);
        if (template == null) return null;

        World world = Bukkit.getWorld(template.worldFolderName());
        if (world == null) {
            world = new WorldCreator("templates/" + template.worldFolderName())
                .generator("VoidGenerator")
                .createWorld();
        }
        if (world != null) {
            applyWorldRules(world);
        }
        return world;
    }

    public static void saveTemplateWorld(World world) {
        if (world.getName().startsWith("templates/")) {
            world.save();
            for (Player p : world.getPlayers()) {
                Heroes.teleportToLobby(p);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.unloadWorld(world, true), 20L);
        }
    }

    public static void loadMapsFromConfig() {
        templateMaps.clear();
        mapPools.clear();
        for (GameMode mode : GameMode.values()) {
            mapPools.put(mode, new LinkedHashSet<>());
        }

        File[] ymlFiles = mapConfigFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (ymlFiles == null || ymlFiles.length == 0) {
            plugin.getLogger().warning("No map configurations found in the maps folder.");
            return;
        }

        for (File file : ymlFiles) {
            try {
                GameMap map = parseMapFromYml(file);
                if (map != null) {
                    templateMaps.add(map);
                    for (GameMode mode : map.supportedModes()) {
                        mapPools.get(mode).add(map);
                    }
                    plugin.getLogger().info("Loaded map template: " + map.name());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Could not parse map configuration: " + file.getName());
                e.printStackTrace();
            }
        }
    }

    private static GameMap parseMapFromYml(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = config.getString("name");
        Material displayMat = Material.valueOf(config.getString("display-item", "GRASS_BLOCK").toUpperCase());
        ItemStack displayItem = new ItemStack(displayMat);

        Set<GameMode> modes = config.getStringList("supported-modes").stream()
            .map(s -> GameMode.valueOf(s.toUpperCase()))
            .collect(Collectors.toSet());

        String worldFolder = config.getString("world-folder");
        String iconModelKey = config.getString("icon-model-key", null);

        BoundingBox boundaries = parseBoundaries(config.getConfigurationSection("boundaries"));
        Location spectatorLoc = parseLocation(config.getConfigurationSection("spectator-location"));

        List<Location> spawns = config.getMapList("spawn-locations").stream()
            .map(GameMapManager::parseLocationFromMap)
            .toList();

        List<Location> crystals = config.getMapList("crystal-locations").stream()
            .map(GameMapManager::parseLocationFromMap)
            .toList();

        return new GameMap(name, displayItem, iconModelKey, modes, worldFolder, boundaries, spectatorLoc, spawns, crystals, null);
    }

    private static Location parseLocationFromMap(Map<?, ?> map) {
        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        double z = ((Number) map.get("z")).doubleValue();
        float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0f;
        float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0f;
        return new Location(null, x, y, z, yaw, pitch);
    }

    private static Location parseLocation(ConfigurationSection section) {
        if (section == null) return null;
        return new Location(null,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw", 0),
            (float) section.getDouble("pitch", 0)
        );
    }

    private static BoundingBox parseBoundaries(ConfigurationSection section) {
        if (section == null) return null;
        ConfigurationSection min = section.getConfigurationSection("min");
        ConfigurationSection max = section.getConfigurationSection("max");
        if (min == null || max == null) return null;

        return new BoundingBox(
            min.getDouble("x"), min.getDouble("y"), min.getDouble("z"),
            max.getDouble("x"), max.getDouble("y"), max.getDouble("z")
        );
    }

    public static boolean updateMapConfig(Player player, String mapName, String[] args) {
        File mapFile = new File(mapConfigFolder, mapName + ".yml");
        if (!mapFile.exists()) {
            player.sendMessage(GameConfigManager.getMessage("error.map-not-found", Map.of("map", mapName)));
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(mapFile);
        String property = args[2].toLowerCase();

        try {
            switch (property) {
                case "spectatorlocation" -> setLocationInConfig(config, "spectator-location", player.getLocation());
                case "spawnlocation" -> {
                    int index = Integer.parseInt(args[3]);
                    updateLocationList(config, "spawn-locations", index, player.getLocation(), player);
                }
                case "crystallocation" -> {
                    int index = Integer.parseInt(args[3]);
                    updateLocationList(config, "crystal-locations", index, player.getLocation(), player);
                }
                case "addspawn" -> addToLocationList(config, "spawn-locations", player.getLocation());
                case "addcrystal" -> addToLocationList(config, "crystal-locations", player.getLocation());
                case "removespawn" -> {
                    int index = Integer.parseInt(args[3]);
                    removeFromLocationList(config, "spawn-locations", index, player);
                }
                case "removecrystal" -> {
                    int index = Integer.parseInt(args[3]);
                    removeFromLocationList(config, "crystal-locations", index, player);
                }
                case "displayitem" -> {
                    String materialName = args[3].toUpperCase();
                    Material.valueOf(materialName);
                    config.set("display-item", materialName);
                }
                case "gamemode" -> {
                    String action = args[3].toLowerCase();
                    String modeName = args[4].toUpperCase();
                    GameMode.valueOf(modeName);

                    List<String> modes = config.getStringList("supported-modes");
                    if (action.equals("add") && !modes.contains(modeName)) {
                        modes.add(modeName);
                    } else if (action.equals("remove")) {
                        modes.remove(modeName);
                    }
                    config.set("supported-modes", modes);
                }
                case "boundary1" -> {
                    boundarySelections.put(player.getUniqueId(), player.getLocation());
                    player.sendMessage(Component.text("Set boundary corner 1. Use '/mapadmin set " + mapName + " boundary2' to finish.", NamedTextColor.GREEN));
                    return true;
                }
                case "boundary2" -> {
                    Location corner1 = boundarySelections.remove(player.getUniqueId());
                    if (corner1 == null) {
                        player.sendMessage(Component.text("Error: You must set boundary corner 1 first.", NamedTextColor.RED));
                        return false;
                    }
                    BoundingBox newBounds = BoundingBox.of(corner1, player.getLocation());
                    config.set("boundaries.min.x", newBounds.getMinX());
                    config.set("boundaries.min.y", newBounds.getMinY());
                    config.set("boundaries.min.z", newBounds.getMinZ());
                    config.set("boundaries.max.x", newBounds.getMaxX());
                    config.set("boundaries.max.y", newBounds.getMaxY());
                    config.set("boundaries.max.z", newBounds.getMaxZ());
                }
                default -> {
                    player.sendMessage(GameConfigManager.getMessage("error.invalid-property", Map.of("property", property)));
                    return false;
                }
            }

            config.save(mapFile);
            // Only THIS map re-parses — /mapadmin reload stays the reload-everything hammer.
            reloadMap(mapName);
            player.sendMessage(Component.text("Map config updated successfully.", NamedTextColor.GREEN));
            return true;

        } catch (Exception e) {
            player.sendMessage(Component.text("Error updating config: " + e.getMessage(), NamedTextColor.RED));
            return false;
        }
    }

    private static void setLocationInConfig(FileConfiguration config, String path, Location loc) {
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }

    private static void updateLocationList(FileConfiguration config, String path, int index, Location loc, Player player) {
        List<Map<?, ?>> locs = config.getMapList(path);
        if (index < 0 || index >= locs.size()) {
            player.sendMessage(GameConfigManager.getMessage("error.invalid-index",
                Map.of("index", String.valueOf(index), "max", String.valueOf(locs.size() - 1))));
            return;
        }

        Map<String, Object> locMap = createLocationMap(loc);
        List<Map<String, Object>> newLocs = new ArrayList<>();
        for (int i = 0; i < locs.size(); i++) {
            newLocs.add(i == index ? locMap : new LinkedHashMap<>((Map<String, Object>) locs.get(i)));
        }
        config.set(path, newLocs);
    }

    private static void addToLocationList(FileConfiguration config, String path, Location loc) {
        List<Map<?, ?>> locs = new ArrayList<>(config.getMapList(path));
        locs.add(createLocationMap(loc));
        config.set(path, locs);
    }

    private static void removeFromLocationList(FileConfiguration config, String path, int index, Player player) {
        List<Map<?, ?>> locs = new ArrayList<>(config.getMapList(path));
        if (index < 0 || index >= locs.size()) {
            player.sendMessage(GameConfigManager.getMessage("error.invalid-index",
                Map.of("index", String.valueOf(index), "max", String.valueOf(locs.size() - 1))));
            return;
        }
        locs.remove(index);
        config.set(path, locs);
    }

    private static Map<String, Object> createLocationMap(Location loc) {
        Map<String, Object> locMap = new LinkedHashMap<>();
        locMap.put("x", loc.getX());
        locMap.put("y", loc.getY());
        locMap.put("z", loc.getZ());
        locMap.put("yaw", loc.getYaw());
        locMap.put("pitch", loc.getPitch());
        return locMap;
    }

    public static String getMapProperty(String mapName, String property, int index) {
        GameMap template = templateMaps.stream()
            .filter(m -> m.name().equalsIgnoreCase(mapName))
            .findFirst()
            .orElse(null);

        if (template == null) {
            return "Error: Map not found.";
        }

        return switch (property.toLowerCase()) {
            case "spectatorlocation" -> formatLocation(template.spectatorLocation());
            case "spawnlocation" -> {
                if (index >= 0 && index < template.spawnLocations().size()) {
                    yield formatLocation(template.spawnLocations().get(index));
                } else {
                    yield "Error: Invalid spawn index.";
                }
            }
            case "crystallocation" -> {
                if (index >= 0 && index < template.crystalLocations().size()) {
                    yield formatLocation(template.crystalLocations().get(index));
                } else {
                    yield "Error: Invalid crystal index.";
                }
            }
            case "boundaries" -> {
                if (template.boundaries() == null) yield "N/A";
                yield "Min: " + formatLocation(template.boundaries().getMin().toLocation(null)) +
                    ", Max: " + formatLocation(template.boundaries().getMax().toLocation(null));
            }
            case "displayitem" -> {
                if (template.displayItem() == null) yield "N/A";
                yield template.displayItem().getType().name();
            }
            case "gamemode", "gamemodes" -> {
                if (template.supportedModes() == null || template.supportedModes().isEmpty()) yield "None";
                yield template.supportedModes().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            }
            default -> "Error: Unknown property.";
        };
    }

    public static List<String> getTemplateMapNames() {
        return templateMaps.stream().map(GameMap::name).toList();
    }

    /**
     * Re-parses ONE map's yml and swaps it into the template list and mode pools in place.
     * /mapadmin set and /mappool edits go through this — a single-map change must not churn
     * every other map the way {@link #loadMapsFromConfig()} (the /mapadmin reload hammer) does.
     */
    public static boolean reloadMap(String mapName) {
        File file = mapConfigFile(mapName);
        if (file == null) return false;
        GameMap fresh = parseMapFromYml(file);
        if (fresh == null) return false;

        templateMaps.removeIf(m -> m.name().equalsIgnoreCase(fresh.name())
                || m.worldFolderName().equals(fresh.worldFolderName()));
        for (Set<GameMap> pool : mapPools.values()) {
            pool.removeIf(m -> m.name().equalsIgnoreCase(fresh.name())
                    || m.worldFolderName().equals(fresh.worldFolderName()));
        }
        templateMaps.add(fresh);
        for (GameMode mode : fresh.supportedModes()) {
            mapPools.computeIfAbsent(mode, k -> new LinkedHashSet<>()).add(fresh);
        }
        return true;
    }

    /**
     * Writes the starter yml for a brand-new map (/ma create). The template world itself is NOT
     * created here — loadTemplateForEditing generates it as a fresh void world on first entry,
     * because the folder doesn't exist yet. supported-modes starts EMPTY on purpose: a new map
     * is invisible to queue rotation and the custom GUI until an op adds it via /mappool.
     */
    public static void createMapConfig(String name) {
        File file = new File(mapConfigFolder, name.toLowerCase() + ".yml");
        if (file.exists()) {
            throw new IllegalStateException("Map config already exists: " + file.getName());
        }
        String yml = """
                # Map Display Information
                name: %s
                display-item: GRASS_BLOCK   # FILL - GUI icon material

                # Supported Game Modes — deliberately empty: invisible to queues and the custom
                # GUI until an op adds it with /mappool <mode> add %s
                supported-modes: []

                # World Template Folder (in /templates/) — generated as a void world on first /ma edit
                world-folder: %s_template

                # Map Boundaries (players die if they leave) — FILL once built (/ma set boundary1 + boundary2)
                boundaries:
                  min:
                    x: -128.0
                    y: -64.0
                    z: -128.0
                  max:
                    x: 128.0
                    y: 320.0
                    z: 128.0

                # Spectator Spawn Location — FILL (/ma set spectatorlocation)
                spectator-location:
                  x: 0.5
                  y: 80.0
                  z: 0.5

                # Team Spawn Locations — FILL (/ma set spawnlocation <index>)
                # Index 0 = Team 1 (RED), Index 1 = Team 2 (BLUE), etc.
                spawn-locations:
                - x: 0.5
                  y: 65.0
                  z: 10.5
                  yaw: 180.0
                  pitch: 0.0
                - x: 0.5
                  y: 65.0
                  z: -9.5
                  yaw: 0.0
                  pitch: 0.0

                # Smash Crystal Spawn Locations — FILL (/ma set crystallocation <index>)
                crystal-locations: []
                """.formatted(name, name.toLowerCase(), name.toLowerCase());
        try {
            java.nio.file.Files.writeString(file.toPath(), yml, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file.getName(), e);
        }
    }

    /** The template map whose world folder backs the given template world, or null. */
    public static GameMap templateByWorld(World world) {
        String name = world.getName();
        if (!name.startsWith("templates/")) return null;
        String folder = name.substring("templates/".length());
        return templateMaps.stream()
                .filter(m -> m.worldFolderName().equals(folder))
                .findFirst().orElse(null);
    }

    /** Resolves a map name (display or file base name, case-insensitive) to its yml file. */
    public static File mapConfigFile(String mapName) {
        File direct = new File(mapConfigFolder, mapName.toLowerCase() + ".yml");
        if (direct.exists()) return direct;
        File[] files = mapConfigFolder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return null;
        for (File f : files) {
            if (f.getName().replace(".yml", "").equalsIgnoreCase(mapName)) return f;
        }
        return null;
    }

    public static GameMap createWorld(GameMap template) {
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
            applyWorldRules(world);
        }

        return template.withWorld(world);
    }

    private static void applyWorldRules(World world) {
        ConfigurationSection rules = GameConfigManager.getGameConfig().getConfigurationSection("world-rules");
        if (rules == null) {
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.KEEP_INVENTORY, true);
            world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
            world.setGameRule(GameRules.ELYTRA_MOVEMENT_CHECK, false);
            world.setGameRule(GameRules.PLAYER_MOVEMENT_CHECK, false);
            world.setGameRule(GameRules.RAIDS, false);
            world.setFullTime(10000L);
            return;
        }

        world.setGameRule(GameRules.SPAWN_MOBS, rules.getBoolean("SPAWN_MOBS", false));
        world.setGameRule(GameRules.ADVANCE_TIME, rules.getBoolean("ADVANCE_TIME", false));
        world.setGameRule(GameRules.ADVANCE_WEATHER, rules.getBoolean("ADVANCE_WEATHER", false));
        world.setGameRule(GameRules.KEEP_INVENTORY, rules.getBoolean("KEEP_INVENTORY", true));
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, rules.getBoolean("IMMEDIATE_RESPAWN", true));
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, rules.getBoolean("NATURAL_HEALTH_REGENERATION", false));
        world.setGameRule(GameRules.ELYTRA_MOVEMENT_CHECK, rules.getBoolean("ELYTRA_MOVEMENT_CHECK", false));
        world.setGameRule(GameRules.PLAYER_MOVEMENT_CHECK, rules.getBoolean("PLAYER_MOVEMENT_CHECK", false));
        world.setGameRule(GameRules.RAIDS, rules.getBoolean("RAIDS", false));
        world.setFullTime(rules.getLong("fixed-time", 10000L));
    }

    public static void deleteWorld(GameMap map) {
        World world = map.world();
        if (world == null) return;

        File worldFolder = world.getWorldFolder();

        if (Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().info("Successfully unloaded world: " + world.getName());
        } else {
            plugin.getLogger().severe("Could not unload world: " + world.getName());
            return;
        }

        // The unload has to stay on the main thread (Bukkit requirement), but the recursive folder
        // delete is a full Files.walk over a world — hundreds of file ops that were stalling the
        // tick at every game end. Handing it off is safe: createWorld mints a per-instance folder
        // (template folder name + "_" + 8 random chars), so a game starting while this runs can
        // never be touching the folder being deleted.
        //
        // During onDisable the plugin is already flagged disabled and the scheduler refuses new
        // tasks (IllegalPluginAccessException), so a /stop mid-game deletes inline instead. The
        // tick stall is irrelevant there, and the alternative is an orphan folder every shutdown.
        if (!plugin.isEnabled()) {
            deleteWorldFolder(worldFolder);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteWorldFolder(worldFolder));
    }

    private static void deleteWorldFolder(File worldFolder) {
        try {
            deleteDirectory(worldFolder);
            plugin.getLogger().info("Successfully deleted world folder: " + worldFolder.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to delete world folder: " + worldFolder.getName());
            e.printStackTrace();
        }
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
