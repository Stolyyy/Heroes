package me.stolyy.heroes.util;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Loud config reads.
 *
 * <p>Bukkit's single-argument getters do NOT throw on a missing key — {@code MemorySection.getDouble(path)}
 * and friends fall through to {@code 0} / {@code 0.0} / {@code null}. Writing
 * {@code config.getDouble("radius")} instead of {@code config.getDouble("radius", 3.0)} therefore does not
 * surface a yml mistake; it silently substitutes a zero, which is usually WORSE than the default it
 * replaced (radius 0 hits nobody, a 0-tick delay reintroduces the bug the key was added to fix).
 *
 * <p>These helpers do what the bare getters look like they do: a missing key fails at hero-load time
 * with the key name and section path in the message, so a yml typo is a startup error rather than an
 * ability that quietly misbehaves.
 *
 * <p>Use them for every value that must come from yml. Genuine opt-in flags (absent really does mean
 * "off") can keep {@code getBoolean(key, false)}.
 */
public final class Configs {
    private Configs() {}

    public static double requireDouble(ConfigurationSection config, String key) {
        check(config, key);
        return config.getDouble(key);
    }

    public static int requireInt(ConfigurationSection config, String key) {
        check(config, key);
        return config.getInt(key);
    }

    public static long requireLong(ConfigurationSection config, String key) {
        check(config, key);
        return config.getLong(key);
    }

    public static String requireString(ConfigurationSection config, String key) {
        check(config, key);
        return config.getString(key);
    }

    public static boolean requireBoolean(ConfigurationSection config, String key) {
        check(config, key);
        return config.getBoolean(key);
    }

    public static ConfigurationSection requireSection(ConfigurationSection config, String key) {
        check(config, key);
        ConfigurationSection section = config.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalStateException("Config key '" + path(config, key) + "' must be a section, not a value");
        }
        return section;
    }

    /** The ability's own value when it sets {@code key}, otherwise the templates.yml value at {@code templatePath}. */
    public static double orTemplate(ConfigurationSection config, String key, String templatePath) {
        if (config != null && config.contains(key)) return config.getDouble(key);
        return TemplateConfig.requireDouble(templatePath);
    }

    public static int orTemplateInt(ConfigurationSection config, String key, String templatePath) {
        if (config != null && config.contains(key)) return config.getInt(key);
        return TemplateConfig.requireInt(templatePath);
    }

    public static long orTemplateLong(ConfigurationSection config, String key, String templatePath) {
        if (config != null && config.contains(key)) return config.getLong(key);
        return TemplateConfig.requireLong(templatePath);
    }

    public static boolean orTemplateBoolean(ConfigurationSection config, String key, String templatePath) {
        if (config != null && config.contains(key)) return config.getBoolean(key);
        return TemplateConfig.requireBoolean(templatePath);
    }

    public static String orTemplateString(ConfigurationSection config, String key, String templatePath) {
        if (config != null && config.contains(key)) return config.getString(key);
        return TemplateConfig.requireString(templatePath);
    }

    private static void check(ConfigurationSection config, String key) {
        if (config == null) {
            throw new IllegalStateException("Missing config section while reading required key '" + key + "'");
        }
        if (!config.contains(key)) {
            throw new IllegalStateException("Missing required config key '" + path(config, key) + "'");
        }
    }

    private static String path(ConfigurationSection config, String key) {
        String parent = config.getCurrentPath();
        return parent == null || parent.isEmpty() ? key : parent + "." + key;
    }
}
