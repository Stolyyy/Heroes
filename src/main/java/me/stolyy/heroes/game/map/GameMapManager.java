package me.stolyy.heroes.game.map;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.ErrorReporter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Map template catalog, YML parsing, and mode/pool lookups. */
public class GameMapManager {
    private static Heroes plugin;
    private static File mapConfigFolder;

    private static final Set<GameMap> templateMaps = new LinkedHashSet<>();
    private static final Map<GameMode, Set<GameMap>> mapPools = new EnumMap<>(GameMode.class);

    /** Initializes map folders, purges stale instance worlds, and loads all map templates. */
    public static void initialize() {
        plugin = Heroes.getInstance();
        mapConfigFolder = new File(plugin.getDataFolder(), "maps");
        File templatesFolder = new File(plugin.getServer().getWorldContainer(), "templates");
        File activeWorldsFolder = new File(plugin.getServer().getWorldContainer(), "active_worlds");

        if (!mapConfigFolder.exists()) mapConfigFolder.mkdirs();
        if (!templatesFolder.exists()) templatesFolder.mkdirs();
        if (!activeWorldsFolder.exists()) activeWorldsFolder.mkdirs();

        WorldInstances.initialize(plugin, templatesFolder, activeWorldsFolder);
        MapAdmin.initialize(plugin, mapConfigFolder);
        WorldInstances.purgeOrphanedInstances();
        loadMapsFromConfig();
    }

    static Set<GameMap> templates() {
        return Collections.unmodifiableSet(templateMaps);
    }

    /** Picks a random template map registered for {@code mode}; throws if that pool is empty. */
    public static GameMap getRandomMap(GameMode mode) {
        Set<GameMap> pool = mapPools.get(mode);
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No maps configured for game mode " + mode);
        }
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

    /** Picks a random template map matching {@code pool}; throws if the resolved pool is empty. */
    public static GameMap getRandomMapForPool(MapPool pool) {
        Set<GameMap> maps = getMapsForPool(pool);
        if (maps.isEmpty()) {
            throw new IllegalStateException("No maps configured for map pool " + pool);
        }
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

    /** Returns all known template map names, sorted alphabetically. */
    public static List<String> getAllMapNames() {
        return templateMaps.stream()
                .map(GameMap::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /** Clears and reloads all {@code maps/*.yml} template configs into the catalog and mode pools. */
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
                templateMaps.add(map);
                for (GameMode mode : map.supportedModes()) {
                    mapPools.get(mode).add(map);
                }
                plugin.getLogger().info("Loaded map template: " + map.name());
            } catch (Exception e) {
                ErrorReporter.report("Could not parse map configuration: " + file.getName(), e);
            }
        }
    }

    /** Re-parses one map's YML and swaps it into the catalog and mode pools in place. */
    public static boolean reloadMap(String mapName) {
        File file = MapAdmin.mapConfigFile(mapName);
        if (file == null) return false;
        GameMap fresh = parseMapFromYml(file);

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

    private static GameMap parseMapFromYml(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = Configs.requireString(config, "name");
        Material displayMat = Material.valueOf(Configs.requireString(config, "display-item").toUpperCase());
        ItemStack displayItem = new ItemStack(displayMat);

        Set<GameMode> modes = config.getStringList("supported-modes").stream()
                .map(s -> GameMode.valueOf(s.toUpperCase()))
                .collect(Collectors.toSet());

        String worldFolder = Configs.requireString(config, "world-folder");
        String iconModelKey = config.getString("icon-model-key", null);

        BoundingBox boundaries = parseBoundaries(Configs.requireSection(config, "boundaries"));
        Location spectatorLoc = parseLocation(Configs.requireSection(config, "spectator-location"));

        List<Location> spawns = config.getMapList("spawn-locations").stream()
                .map(GameMapManager::parseLocationFromMap)
                .toList();

        List<Location> crystals = config.getMapList("crystal-locations").stream()
                .map(GameMapManager::parseLocationFromMap)
                .toList();

        return new GameMap(name, displayItem, iconModelKey, modes, worldFolder, boundaries, spectatorLoc, spawns, crystals, null);
    }

    private static Location parseLocationFromMap(Map<?, ?> map) {
        if (!(map.get("x") instanceof Number nx) || !(map.get("y") instanceof Number ny) || !(map.get("z") instanceof Number nz)) {
            throw new IllegalStateException("Map location entry missing numeric x/y/z: " + map);
        }
        float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0f;
        float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0f;
        return new Location(null, nx.doubleValue(), ny.doubleValue(), nz.doubleValue(), yaw, pitch);
    }

    private static Location parseLocation(ConfigurationSection section) {
        return new Location(null,
                Configs.requireDouble(section, "x"),
                Configs.requireDouble(section, "y"),
                Configs.requireDouble(section, "z"),
                (float) section.getDouble("yaw", 0),
                (float) section.getDouble("pitch", 0)
        );
    }

    private static BoundingBox parseBoundaries(ConfigurationSection section) {
        ConfigurationSection min = Configs.requireSection(section, "min");
        ConfigurationSection max = Configs.requireSection(section, "max");

        return new BoundingBox(
                Configs.requireDouble(min, "x"),
                Configs.requireDouble(min, "y"),
                Configs.requireDouble(min, "z"),
                Configs.requireDouble(max, "x"),
                Configs.requireDouble(max, "y"),
                Configs.requireDouble(max, "z")
        );
    }
}
