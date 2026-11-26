package me.stolyy.heroes.game.map;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.util.YmlSurgeon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Admin template editing, map YML creation, and {@code /mapadmin} config property updates. */
public final class MapAdmin {
    public static final String TEMPLATES_PREFIX = "templates/";
    public static final List<String> EDITABLE_PROPERTIES = List.of(
            "spectatorlocation", "spawnlocation", "crystallocation", "boundaries", "boundary1", "boundary2");

    private static Heroes plugin;
    private static File mapConfigFolder;
    private static final Map<UUID, Location> boundarySelections = new HashMap<>();

    public record PoolChange(GameMode mode, boolean changed, @Nullable String error) {}

    private MapAdmin() {}

    static void initialize(Heroes pluginInstance, File configFolder) {
        plugin = pluginInstance;
        mapConfigFolder = configFolder;
    }

    public static String mapPermissionNode(String mapName) {
        return "heroes.mapadmin.map." + mapName.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    public static World loadTemplateForEditing(String mapName) {
        GameMap template = GameMapManager.findMapByName(mapName);
        if (template == null) return null;

        World world = Bukkit.getWorld(template.worldFolderName());
        if (world == null) {
            world = new WorldCreator(TEMPLATES_PREFIX + template.worldFolderName())
                    .generator("VoidGenerator")
                    .createWorld();
        }
        if (world != null) {
            WorldInstances.applyWorldRules(world);
        }
        return world;
    }

    public static void saveTemplateWorld(World world) {
        if (!world.getName().startsWith(TEMPLATES_PREFIX)) return;
        world.save();
        for (Player p : world.getPlayers()) {
            Heroes.teleportToLobby(p);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.unloadWorld(world, true), 20L);
    }

    /** Creates the map config, registers the map, generates its void template world, and places a starter platform. */
    public static World createMap(String name) {
        createMapConfig(name);
        GameMapManager.reloadMap(name);
        World world = loadTemplateForEditing(name);
        if (world == null) {
            throw new IllegalStateException("Config created for '" + name + "', but template world failed to load");
        }
        world.getBlockAt(0, 64, 0).setType(Material.STONE);
        world.setSpawnLocation(0, 65, 0);
        return world;
    }

    /** Adds or removes {@code modes} in a map's {@code supported-modes} list and reloads on any change. */
    public static List<PoolChange> setPoolMembership(String mapName, List<GameMode> modes, boolean add) {
        File mapFile = mapConfigFile(mapName);
        GameMap map = GameMapManager.findMapByName(mapName);
        if (mapFile == null || map == null) {
            throw new IllegalArgumentException("Map not found: " + mapName);
        }

        List<PoolChange> results = new ArrayList<>(modes.size());
        boolean changed = false;
        for (GameMode mode : modes) {
            boolean present = map.supportedModes().contains(mode);
            if (add && present) {
                results.add(new PoolChange(mode, false, "'" + mode.name() + "' is already in supported-modes of " + mapFile.getName()));
                continue;
            }
            if (!add && !present) {
                results.add(new PoolChange(mode, false, "'" + mode.name() + "' not found in supported-modes of " + mapFile.getName()));
                continue;
            }
            if (add) {
                YmlSurgeon.addListEntry(mapFile, new String[]{"supported-modes"}, mode.name());
            } else {
                YmlSurgeon.removeListEntry(mapFile, new String[]{"supported-modes"}, mode.name());
            }
            results.add(new PoolChange(mode, true, null));
            changed = true;
        }
        if (changed) GameMapManager.reloadMap(mapName);
        return results;
    }

    public static boolean updateMapConfig(Player player, String mapName, String[] args) {
        File mapFile = mapConfigFile(mapName);
        if (mapFile == null) {
            player.sendMessage(GameConfigManager.getMessage("error.map-not-found", Map.of("map", mapName)));
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(mapFile);
        String property = args[2].toLowerCase();

        try {
            switch (property) {
                case "spectatorlocation" -> setLocationInConfig(config, "spectator-location", player.getLocation());
                case "spawnlocation" -> updateLocationList(config, "spawn-locations", Integer.parseInt(args[3]), player.getLocation(), player);
                case "crystallocation" -> updateLocationList(config, "crystal-locations", Integer.parseInt(args[3]), player.getLocation(), player);
                case "addspawn" -> addToLocationList(config, "spawn-locations", player.getLocation());
                case "addcrystal" -> addToLocationList(config, "crystal-locations", player.getLocation());
                case "removespawn" -> removeFromLocationList(config, "spawn-locations", Integer.parseInt(args[3]), player);
                case "removecrystal" -> removeFromLocationList(config, "crystal-locations", Integer.parseInt(args[3]), player);
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
            GameMapManager.reloadMap(mapName);
            player.sendMessage(Component.text("Map config updated successfully.", NamedTextColor.GREEN));
            return true;
        } catch (Exception e) {
            player.sendMessage(Component.text("Error updating config: " + e.getMessage(), NamedTextColor.RED));
            return false;
        }
    }

    public static String getMapProperty(String mapName, String property, int index) {
        GameMap template = GameMapManager.findMapByName(mapName);
        if (template == null) return "Error: Map not found.";

        return switch (property.toLowerCase()) {
            case "spectatorlocation" -> formatLocation(template.spectatorLocation());
            case "spawnlocation" -> (index >= 0 && index < template.spawnLocations().size())
                    ? formatLocation(template.spawnLocations().get(index))
                    : "Error: Invalid spawn index.";
            case "crystallocation" -> (index >= 0 && index < template.crystalLocations().size())
                    ? formatLocation(template.crystalLocations().get(index))
                    : "Error: Invalid crystal index.";
            case "boundaries" -> template.boundaries() == null ? "N/A"
                    : "Min: " + formatLocation(template.boundaries().getMin().toLocation(null))
                    + ", Max: " + formatLocation(template.boundaries().getMax().toLocation(null));
            case "displayitem" -> template.displayItem() == null ? "N/A" : template.displayItem().getType().name();
            case "gamemode", "gamemodes" -> (template.supportedModes() == null || template.supportedModes().isEmpty())
                    ? "None"
                    : template.supportedModes().stream().map(Enum::name).collect(Collectors.joining(", "));
            default -> "Error: Unknown property.";
        };
    }

    /** Writes the starter YML for {@code /ma create}; supported-modes starts empty. */
    public static void createMapConfig(String name) {
        File file = new File(mapConfigFolder, name.toLowerCase() + ".yml");
        if (file.exists()) {
            throw new IllegalStateException("Map config already exists: " + file.getName());
        }
        String yml = """
                # Map Display Information
                name: %s
                display-item: GRASS_BLOCK   # FILL - GUI icon material

                # Supported Game Modes — empty until added via /mappool <mode> add %s
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
            Files.writeString(file.toPath(), yml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file.getName(), e);
        }
    }

    /** The template map whose world folder backs {@code world}, or null. */
    public static GameMap templateByWorld(World world) {
        String name = world.getName();
        if (!name.startsWith(TEMPLATES_PREFIX)) return null;
        String folder = name.substring(TEMPLATES_PREFIX.length());
        return GameMapManager.templates().stream()
                .filter(m -> m.worldFolderName().equals(folder))
                .findFirst().orElse(null);
    }

    /** Resolves a map name (display or file base name, case-insensitive) to its YML file. */
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

    private static void setLocationInConfig(FileConfiguration config, String path, Location loc) {
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }

    @SuppressWarnings("unchecked")
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

    private static String formatLocation(Location loc) {
        if (loc == null) return "N/A";
        return String.format("X: %.1f, Y: %.1f, Z: %.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}
