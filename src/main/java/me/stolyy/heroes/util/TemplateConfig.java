package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * templates.yml: defaults shared by every hero for template-level keys. A hero's own yml overrides
 * any of them per ability through {@link Configs#orTemplate}. Reloaded with the rest of the configs.
 */
public final class TemplateConfig {
    private static FileConfiguration config;

    private TemplateConfig() {}

    public static void reload() {
        Heroes plugin = Heroes.getInstance();
        File file = new File(plugin.getDataFolder(), "templates.yml");
        if (!file.exists()) plugin.saveResource("templates.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static double requireDouble(String path) {
        return Configs.requireDouble(section(path), key(path));
    }

    public static long requireLong(String path) {
        return Configs.requireLong(section(path), key(path));
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
