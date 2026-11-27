package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.HeroConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * templates.yml: defaults shared by every hero for template-level keys. A hero's own yml overrides
 * any of them per ability through {@link Configs#orTemplate}. Reloaded with the rest of the configs.
 */
public final class TemplateConfig {
    private static FileConfiguration config;

    private TemplateConfig() {}

    public static File getFile() {
        return new File(Heroes.getInstance().getDataFolder(), "templates.yml");
    }

    public static void reload() {
        Heroes plugin = Heroes.getInstance();
        File file = getFile();
        if (!file.exists()) plugin.saveResource("templates.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static double requireDouble(String path) {
        return Configs.requireDouble(section(path), key(path));
    }

    public static int requireInt(String path) {
        return Configs.requireInt(section(path), key(path));
    }

    public static long requireLong(String path) {
        return Configs.requireLong(section(path), key(path));
    }

    public static boolean requireBoolean(String path) {
        return Configs.requireBoolean(section(path), key(path));
    }

    public static String requireString(String path) {
        return Configs.requireString(section(path), key(path));
    }

    public static ConfigurationSection requireSection(String path) {
        return Configs.requireSection(section(path), key(path));
    }

    public static List<String> getConfigKeys(String path) {
        if (config == null) return Collections.emptyList();
        if (path.isEmpty()) return new ArrayList<>(config.getKeys(false));
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? new ArrayList<>(section.getKeys(false)) : Collections.emptyList();
    }

    public static String getValue(String path) {
        if (config == null || path.isEmpty()) return null;
        Object value = config.get(path);
        if (value == null || value instanceof ConfigurationSection
                || value instanceof List || value instanceof Map) {
            return null;
        }
        return value.toString();
    }

    public static boolean setValue(String path, String value) {
        File file = getFile();
        if (!file.exists()) return false;

        String oldValue = getValue(path);
        if (oldValue == null) {
            throw new IllegalStateException("'" + path + "' is not an existing value in templates.yml"
                    + " — /balance can edit existing keys only");
        }
        if (oldValue.equalsIgnoreCase(value)) {
            return true;
        }

        String original;
        try {
            original = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.getName(), e);
        }

        YmlSurgeon.setScalar(file, path.split("\\."), value);
        reload();

        String now = getValue(path);
        if (now == null || !now.equalsIgnoreCase(value)) {
            try {
                Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Bad edit AND restore failed for " + file.getName(), e);
            }
            reload();
            throw new IllegalStateException("Edit verification failed ('" + path + "' reads back as "
                    + now + ") — " + file.getName() + " was restored unchanged");
        }
        HeroConfig.load();
        PatchNotesLog.record("templates", path, oldValue, now);
        return true;
    }

    public static boolean handleConfigCommand(Player player, String[] pathArgs) {
        if (pathArgs.length == 0) {
            List<String> keys = getConfigKeys("");
            player.sendMessage(Component.text("Available keys: " + String.join(", ", keys), NamedTextColor.AQUA));
            return true;
        }

        String fullPath = String.join(".", pathArgs);
        String currentValue = getValue(fullPath);
        if (currentValue != null) {
            player.sendMessage(Component.text(fullPath + " = " + currentValue, NamedTextColor.GREEN));
            return true;
        }

        List<String> children = getConfigKeys(fullPath);
        if (!children.isEmpty()) {
            player.sendMessage(Component.text(fullPath + " children: " + String.join(", ", children), NamedTextColor.AQUA));
            return true;
        }

        if (pathArgs.length >= 2) {
            String pathWithoutLast = String.join(".", Arrays.copyOfRange(pathArgs, 0, pathArgs.length - 1));
            String value = pathArgs[pathArgs.length - 1];
            try {
                if (setValue(pathWithoutLast, value)) {
                    player.sendMessage(Component.text("Set " + pathWithoutLast + " = " + value, NamedTextColor.GREEN));
                    return true;
                }
            } catch (IllegalStateException e) {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                return true;
            }
        }

        player.sendMessage(Component.text("Path not found: " + fullPath, NamedTextColor.RED));
        return true;
    }

    private static ConfigurationSection section(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return config;
        ConfigurationSection section = config.getConfigurationSection(path.substring(0, dot));
        if (section == null) throw new IllegalStateException("templates.yml has no section '" + path.substring(0, dot) + "'");
        return section;
    }

    private static String key(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
