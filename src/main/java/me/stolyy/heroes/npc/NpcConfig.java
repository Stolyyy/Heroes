package me.stolyy.heroes.npc;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.util.Configs;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and parses NPC configuration from lobby.yml.
 */
public class NpcConfig {

    private static YamlConfiguration config;

    /**
     * Loads the NPC configuration file.
     */
    public static void load() {
        File file = new File(Heroes.getInstance().getDataFolder(), "lobby.yml");

        if (!file.exists()) {
            Heroes.getInstance().saveResource("lobby.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
        Heroes.getInstance().getLogger().info("Loaded NPC configuration");
    }

    /**
     * Gets the announcer NPC data.
     */
    public static AnnouncerData getAnnouncerData() {
        ConfigurationSection section = config.getConfigurationSection("announcer");
        if (section == null) return null;

        String name = Configs.requireString(section, "name");
        Location location = parseLocation(Configs.requireSection(section, "location"));
        String skin = Configs.requireString(section, "skin");

        return new AnnouncerData(name, location, skin);
    }

    /**
     * Gets all hero NPC data entries.
     */
    public static List<HeroNpcData> getHeroNpcData() {
        List<HeroNpcData> result = new ArrayList<>();

        ConfigurationSection heroesSection = config.getConfigurationSection("heroes");
        if (heroesSection == null) return result;

        for (String key : heroesSection.getKeys(false)) {
            ConfigurationSection section = heroesSection.getConfigurationSection(key);
            if (section == null) continue;

            String name = Configs.requireString(section, "name");
            String hero = Configs.requireString(section, "hero");
            Location location = parseLocation(Configs.requireSection(section, "location"));

            // Parse armor
            ArmorData armor = parseArmor(section.getConfigurationSection("armor"));

            result.add(new HeroNpcData(key, name, hero, location, armor));
        }

        return result;
    }

    /**
     * Gets all queue NPC entries ({@code queue-npcs:} in lobby.yml — keys 1v1/2v2/custom).
     */
    public static List<QueueNpcData> getQueueNpcData() {
        List<QueueNpcData> result = new ArrayList<>();

        ConfigurationSection root = config.getConfigurationSection("queue-npcs");
        if (root == null) return result;

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;

            GameMode mode = switch (key.toLowerCase()) {
                case "1v1" -> GameMode.ONE_V_ONE;
                case "2v2" -> GameMode.TWO_V_TWO;
                case "custom" -> GameMode.CUSTOM;
                default -> throw new IllegalStateException("Unknown queue-npcs mode in lobby.yml: '" + key + "'");
            };

            Location location = parseLocation(Configs.requireSection(section, "location"));
            String name = Configs.requireString(section, "name");
            String skin = Configs.requireString(section, "skin");
            result.add(new QueueNpcData(mode, name, location, skin));
        }

        return result;
    }

    public record QueueNpcData(GameMode mode, String name,
                               Location location, String skin) {}

    /**
     * Gets the lobby spawn location from {@code lobby.spawn} in lobby.yml.
     * Throws loudly if the section, coordinates, or world are missing.
     */
    public static Location getLobbySpawn() {
        ConfigurationSection section = Configs.requireSection(config, "lobby.spawn");
        String worldName = Configs.requireString(section, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Lobby spawn world not found: " + worldName);
        }
        double x = Configs.requireDouble(section, "x");
        double y = Configs.requireDouble(section, "y");
        double z = Configs.requireDouble(section, "z");
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static Location parseLocation(ConfigurationSection section) {
        if (section == null) return null;

        String worldName = Configs.requireString(section, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World not found for " + section.getCurrentPath() + ": " + worldName);
        }

        double x = Configs.requireDouble(section, "x");
        double y = Configs.requireDouble(section, "y");
        double z = Configs.requireDouble(section, "z");
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private static ArmorData parseArmor(ConfigurationSection section) {
        if (section == null) return null;

        // Parse head model data
        ConfigurationSection headSection = section.getConfigurationSection("head");
        HeadData head = null;
        if (headSection != null) {
            head = new HeadData(
                    headSection.getString("hero"),
                    headSection.getString("texture"),
                    headSection.getString("skin")
            );
        }

        // Parse armor color
        Color color = null;
        if (section.contains("color")) {
            String colorStr = Configs.requireString(section, "color");
            String[] parts = colorStr.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid RGB armor color in lobby.yml at '"
                        + section.getCurrentPath() + ".color': '" + colorStr + "'");
            }
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            color = Color.fromRGB(r, g, b);
        }

        return new ArmorData(head, color);
    }

    // Data classes

    public record AnnouncerData(String name, Location location, String skin) {}

    public record HeroNpcData(String id, String name, String hero, Location location, ArmorData armor) {}

    public record ArmorData(HeadData head, Color color) {}

    public record HeadData(String hero, String texture, String skin) {}
}
