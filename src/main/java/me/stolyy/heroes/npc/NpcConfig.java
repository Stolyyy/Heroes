package me.stolyy.heroes.npc;

import me.stolyy.heroes.Heroes;
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

        String name = section.getString("name", "<gold>Lobby Menu</gold>");
        Location location = parseLocation(section.getConfigurationSection("location"));
        String skin = section.getString("skin", "MHF_Steve");

        if (location == null) return null;

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

            String name = section.getString("name", "<gold>" + key + "</gold>");
            String hero = section.getString("hero", key);
            Location location = parseLocation(section.getConfigurationSection("location"));

            if (location == null) {
                Heroes.getInstance().getLogger().warning("Invalid location for NPC: " + key);
                continue;
            }

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

            me.stolyy.heroes.game.GameMode mode = switch (key.toLowerCase()) {
                case "1v1" -> me.stolyy.heroes.game.GameMode.ONE_V_ONE;
                case "2v2" -> me.stolyy.heroes.game.GameMode.TWO_V_TWO;
                case "custom" -> me.stolyy.heroes.game.GameMode.CUSTOM;
                default -> null;
            };
            if (mode == null) {
                Heroes.getInstance().getLogger().warning("Unknown queue NPC mode: " + key);
                continue;
            }

            Location location = parseLocation(section.getConfigurationSection("location"));
            if (location == null) {
                Heroes.getInstance().getLogger().warning("Invalid location for queue NPC: " + key);
                continue;
            }

            String name = section.getString("name", "<gold><bold>" + mode.displayName() + "</bold></gold>");
            String skin = section.getString("skin");
            result.add(new QueueNpcData(mode, name, location, skin));
        }

        return result;
    }

    public record QueueNpcData(me.stolyy.heroes.game.GameMode mode, String name,
                               Location location, String skin) {}

    /**
     * Parses a location from a configuration section.
     */
    private static Location parseLocation(ConfigurationSection section) {
        if (section == null) return null;

        String worldName = section.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Heroes.getInstance().getLogger().warning("World not found: " + worldName);
            return null;
        }

        double x = section.getDouble("x", 0);
        double y = section.getDouble("y", 0);
        double z = section.getDouble("z", 0);
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Parses armor data from a configuration section.
     */
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
        String colorStr = section.getString("color");
        if (colorStr != null && colorStr.contains(",")) {
            String[] parts = colorStr.split(",");
            if (parts.length == 3) {
                try {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    color = Color.fromRGB(r, g, b);
                } catch (NumberFormatException ignored) {}
            }
        }

        return new ArmorData(head, color);
    }

    /**
     * Reloads the configuration.
     */
    public static void reload() {
        load();
    }

    // Data classes

    public record AnnouncerData(String name, Location location, String skin) {}

    public record HeroNpcData(String id, String name, String hero, Location location, ArmorData armor) {}

    public record ArmorData(HeadData head, Color color) {}

    public record HeadData(String hero, String texture, String skin) {}
}
