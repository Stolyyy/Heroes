package me.stolyy.heroes.game;

import me.stolyy.heroes.util.Configs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One entry of game-config.yml {@code practice-arenas}: a bounding box, a spawn and its target dummies. */
public record PracticeArena(String name, String worldName,
                            double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ,
                            double spawnX, double spawnY, double spawnZ, float spawnYaw,
                            List<Vector> dummies) {

    public static PracticeArena parse(ConfigurationSection sec) {
        ConfigurationSection spawn = Configs.requireSection(sec, "spawn");
        List<Vector> dummies = new ArrayList<>();
        for (Map<?, ?> map : sec.getMapList("dummies")) {
            dummies.add(new Vector(((Number) map.get("x")).doubleValue(),
                    ((Number) map.get("y")).doubleValue(),
                    ((Number) map.get("z")).doubleValue()));
        }
        return new PracticeArena(
                Configs.requireString(sec, "name"), Configs.requireString(sec, "world"),
                Configs.requireDouble(sec, "min-x"), Configs.requireDouble(sec, "min-y"), Configs.requireDouble(sec, "min-z"),
                Configs.requireDouble(sec, "max-x"), Configs.requireDouble(sec, "max-y"), Configs.requireDouble(sec, "max-z"),
                Configs.requireDouble(spawn, "x"), Configs.requireDouble(spawn, "y"), Configs.requireDouble(spawn, "z"),
                (float) Configs.requireDouble(spawn, "yaw"), dummies);
    }

    public boolean contains(Location loc) {
        return loc.getWorld() != null && loc.getWorld().getName().equals(worldName)
                && loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private World world() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Practice arena '" + name + "' references unloaded world '" + worldName + "'");
        }
        return world;
    }

    public Location spawn() {
        return new Location(world(), spawnX, spawnY, spawnZ, spawnYaw, 0f);
    }

    public List<Location> dummyLocations() {
        World world = world();
        return dummies.stream().map(v -> v.toLocation(world)).toList();
    }

    /** Distance along {@code direction} (unit) from {@code start} to where the ray leaves the box, capped at {@code range}. */
    public double clampRange(Location start, Vector direction, double range) {
        double exit = range;
        exit = Math.min(exit, slabExit(start.getX(), direction.getX(), minX, maxX));
        exit = Math.min(exit, slabExit(start.getY(), direction.getY(), minY, maxY));
        exit = Math.min(exit, slabExit(start.getZ(), direction.getZ(), minZ, maxZ));
        return Math.max(0, exit);
    }

    private static double slabExit(double origin, double dir, double min, double max) {
        if (dir > 1.0E-9) return (max - origin) / dir;
        if (dir < -1.0E-9) return (min - origin) / dir;
        return Double.POSITIVE_INFINITY;
    }
}
