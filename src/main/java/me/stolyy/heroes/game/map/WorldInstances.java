package me.stolyy.heroes.game.map;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.ErrorReporter;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-match temporary world cloning, gamerule application, teardown, and boot-time orphan cleanup. */
public final class WorldInstances {
    private static final List<Map.Entry<String, GameRule<Boolean>>> BOOLEAN_RULES = List.of(
            Map.entry("SPAWN_MOBS", GameRules.SPAWN_MOBS),
            Map.entry("ADVANCE_TIME", GameRules.ADVANCE_TIME),
            Map.entry("ADVANCE_WEATHER", GameRules.ADVANCE_WEATHER),
            Map.entry("KEEP_INVENTORY", GameRules.KEEP_INVENTORY),
            Map.entry("IMMEDIATE_RESPAWN", GameRules.IMMEDIATE_RESPAWN),
            Map.entry("NATURAL_HEALTH_REGENERATION", GameRules.NATURAL_HEALTH_REGENERATION),
            Map.entry("ELYTRA_MOVEMENT_CHECK", GameRules.ELYTRA_MOVEMENT_CHECK),
            Map.entry("PLAYER_MOVEMENT_CHECK", GameRules.PLAYER_MOVEMENT_CHECK),
            Map.entry("RAIDS", GameRules.RAIDS)
    );

    private static Heroes plugin;
    private static File templatesFolder;
    private static File activeWorldsFolder;

    private WorldInstances() {}

    static void initialize(Heroes pluginInstance, File templates, File activeWorlds) {
        plugin = pluginInstance;
        templatesFolder = templates;
        activeWorldsFolder = activeWorlds;
    }

    /** Deletes every per-game instance folder left behind by a previous server run or crash. */
    static void purgeOrphanedInstances() {
        File[] folders = activeWorldsFolder.listFiles(File::isDirectory);
        if (folders == null || folders.length == 0) return;

        int deleted = 0;
        for (File folder : folders) {
            for (World loaded : new ArrayList<>(Bukkit.getWorlds())) {
                if (loaded.getWorldFolder().getAbsoluteFile().equals(folder.getAbsoluteFile())) {
                    Bukkit.unloadWorld(loaded, false);
                }
            }
            if (discardFolder(folder)) deleted++;
        }
        plugin.getLogger().info("Deleted " + deleted + " orphaned instance world(s) from " + activeWorldsFolder.getName());
    }

    public static GameMap createWorld(GameMap template) {
        String worldName = template.worldFolderName() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File templateWorldFolder = new File(templatesFolder, template.worldFolderName());
        File newWorldFolder = new File(activeWorldsFolder, worldName);

        if (!templateWorldFolder.exists()) {
            throw new IllegalStateException("Cannot create world for map '" + template.name()
                    + "': template folder not found at " + templateWorldFolder.getPath());
        }

        try {
            copyDirectory(templateWorldFolder, newWorldFolder);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy template world '" + template.worldFolderName() + "'", e);
        }

        World world = new WorldCreator(activeWorldsFolder.getName() + "/" + newWorldFolder.getName()).createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to load Bukkit world for map '" + template.name()
                    + "' from " + newWorldFolder.getPath());
        }
        applyWorldRules(world);
        return template.withWorld(world);
    }

    static void applyWorldRules(World world) {
        ConfigurationSection rules = Configs.requireSection(GameConfigManager.getGameConfig(), "world-rules");
        for (Map.Entry<String, GameRule<Boolean>> entry : BOOLEAN_RULES) {
            world.setGameRule(entry.getValue(), Configs.requireBoolean(rules, entry.getKey()));
        }
        world.setFullTime(Configs.requireLong(rules, "fixed-time"));
    }

    public static void deleteWorld(GameMap map) {
        World world = map.world();
        if (world == null) return;

        File worldFolder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            ErrorReporter.report("Could not unload world: " + world.getName());
            return;
        }
        plugin.getLogger().info("Successfully unloaded world: " + world.getName());

        // Main-thread unload is followed by async folder cleanup unless disabling (scheduler refuses new tasks).
        if (!plugin.isEnabled()) {
            discardFolder(worldFolder);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> discardFolder(worldFolder));
    }

    private static boolean discardFolder(File worldFolder) {
        try {
            deleteDirectory(worldFolder);
            plugin.getLogger().info("Deleted world instance folder: " + worldFolder.getName());
            return true;
        } catch (IOException e) {
            ErrorReporter.report("Failed to delete world folder: " + worldFolder.getName(), e);
            return false;
        }
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (!target.exists()) target.mkdirs();
        Path sourceRoot = source.toPath();
        Path targetRoot = target.toPath();
        try (var stream = Files.walk(sourceRoot)) {
            for (Path sourcePath : (Iterable<Path>) stream::iterator) {
                String fileName = sourcePath.getFileName().toString();
                if (fileName.equalsIgnoreCase("uid.dat") || fileName.equalsIgnoreCase("session.lock")) {
                    continue;
                }
                Path targetPath = targetRoot.resolve(sourceRoot.relativize(sourcePath));
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteDirectory(File directory) throws IOException {
        try (var stream = Files.walk(directory.toPath())) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
