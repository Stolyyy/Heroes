package me.stolyy.heroes.hero;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.YmlSurgeon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles reading/writing to hero yml files for the balance command,
 * and owns all hero config loading/lookup state.
 */
public class HeroConfig {

    private static final Map<String, YamlConfiguration> heroConfigs = new HashMap<>();
    // Concurrent: read off-thread via PlayerStats.smashLevel() during async board seeding, autosave, and chat formatting.
    private static final Set<String> adminOnlyHeroes = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> HERO_ALIASES = Map.ofEntries(
            Map.entry("void", "voidcrawler"),
            Map.entry("skull", "skullfire"),
            Map.entry("spiderman", "spooderman"),
            Map.entry("spidey", "spooderman"),
            Map.entry("spider", "spooderman")
    );
    private static YamlConfiguration defaultConfig;

    // -----------------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------------

    public static void load() {
        heroConfigs.clear();
        adminOnlyHeroes.clear();

        File heroesFolder = new File(Heroes.getInstance().getDataFolder(), "heroes");
        if (!heroesFolder.exists()) {
            heroesFolder.mkdirs();
        }

        // Load Default Config
        File defaultFile = new File(heroesFolder, "default.yml");
        if (!defaultFile.exists()) {
            throw new IllegalStateException("Missing required heroes/default.yml in " + heroesFolder.getPath());
        }
        defaultConfig = YamlConfiguration.loadConfiguration(defaultFile);

        // Load Hero Configs
        File[] files = heroesFolder.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("default.yml"));
        if (files == null) return;

        for (File file : files) {
            String heroName = file.getName().replace(".yml", "");
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.setDefaults(defaultConfig);
                config.options().copyDefaults(true);

                heroConfigs.put(heroName.toLowerCase(), config);
                if (config.getBoolean("admin-only", false)) {
                    adminOnlyHeroes.add(heroName.toLowerCase());
                }
                Heroes.getInstance().getLogger().info("Loaded hero archetype: " + heroName);
            } catch (Exception e) {
                Heroes.getInstance().getLogger().log(Level.SEVERE, "Error loading hero file: " + file.getName(), e);
            }
        }
    }

    /**
     * Reloads the config archetype for a single hero without touching active hero instances.
     */
    public static void reload(String heroName) {
        // default.yml is the fallback layer UNDER every hero config — an edit there invalidates
        // all loaded archetypes, so it escalates to the full reload. (Putting default.yml into
        // the hero map under "default", as this used to, left defaultConfig itself stale.)
        if (heroName.equalsIgnoreCase("default")) {
            load();
            return;
        }

        File heroesFolder = new File(Heroes.getInstance().getDataFolder(), "heroes");
        File file = new File(heroesFolder, heroName.toLowerCase() + ".yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (defaultConfig != null) {
            config.setDefaults(defaultConfig);
            config.options().copyDefaults(true);
        }
        heroConfigs.put(heroName.toLowerCase(), config);
        // Keep the admin-only registry in step — load() derives it, so the per-hero path must too.
        if (config.getBoolean("admin-only", false)) adminOnlyHeroes.add(heroName.toLowerCase());
        else adminOnlyHeroes.remove(heroName.toLowerCase());
    }

    public static void clear() {
        heroConfigs.clear();
        adminOnlyHeroes.clear();
    }

    // -----------------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------------

    public static YamlConfiguration getConfig(String heroName) {
        if (heroName.equalsIgnoreCase("default")) return defaultConfig;
        return heroConfigs.get(heroName.toLowerCase());
    }

    public static File getFile(String heroName) {
        return new File(Heroes.getInstance().getDataFolder(), "heroes/" + heroName.toLowerCase() + ".yml");
    }

    public static List<String> getAvailableNames() {
        return new ArrayList<>(heroConfigs.keySet());
    }

    /** Resolves an optional command alias to its canonical lowercase hero id. */
    public static String resolve(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return HERO_ALIASES.getOrDefault(lower, lower);
    }

    public static Map<String, String> aliases() {
        return HERO_ALIASES;
    }

    /** Underscore-escaped ability display names configured on {@code heroName}, plus {@code Jab}. */
    public static List<String> abilityDisplayNames(String heroName) {
        List<String> names = new ArrayList<>();
        names.add("Jab");
        YamlConfiguration config = getConfig(heroName);
        if (config == null) return names;
        for (String slot : List.of("primary", "secondary", "smash")) {
            String name = config.getString("abilities." + slot + ".name");
            if (name != null && !name.isEmpty()) names.add(name.replace(' ', '_'));
        }
        for (Map<?, ?> passive : config.getMapList("abilities.passives")) {
            Object name = passive.get("name");
            if (name != null && !name.toString().isEmpty()) names.add(name.toString().replace(' ', '_'));
        }
        return names;
    }

    public static boolean exists(String heroName) {
        if (heroName.equalsIgnoreCase("default")) return defaultConfig != null;
        return heroConfigs.containsKey(heroName.toLowerCase());
    }

    public static boolean isAdminOnly(String heroName) {
        return adminOnlyHeroes.contains(heroName.toLowerCase());
    }

    // Unlock economy: per-hero unlock block, falling back to default.yml's via copyDefaults.
    private static YamlConfiguration requireHeroConfig(String heroName) {
        YamlConfiguration config = getConfig(heroName);
        if (config == null) {
            throw new IllegalStateException("Unknown hero config: " + heroName);
        }
        return config;
    }

    public static boolean isDefaultUnlocked(String heroName) {
        return Configs.requireBoolean(requireHeroConfig(heroName), "unlock.default-unlocked");
    }

    public static int getUnlockCost(String heroName) {
        return Configs.requireInt(requireHeroConfig(heroName), "unlock.token-cost");
    }

    public static int getSmashLevelRequirement(String heroName) {
        return Configs.requireInt(requireHeroConfig(heroName), "unlock.smash-level-requirement");
    }

    /** Display name: the hero yml's {@code name:} field when present (so renaming a hero
     *  visually is a one-line yml edit), else the prettified key ("heavy_blow" → "Heavy Blow"). */
    public static String formatName(String heroName) {
        YamlConfiguration config = getConfig(heroName.toLowerCase());
        String display = config != null ? config.getString("name") : null;
        if (display != null && !display.isEmpty()) return display;
        return Arrays.stream(heroName.split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * The resource-pack key segment for a hero: lowercase display name, spaces stripped —
     * matches what CosmeticManager emits from Hero.getName(), so skin previews and equipped
     * gear always agree even when the display name differs from the config key (pokita).
     */
    public static String assetKey(String heroName) {
        YamlConfiguration config = getConfig(heroName.toLowerCase());
        String display = config != null ? config.getString("name") : null;
        if (display == null || display.isEmpty()) return heroName.toLowerCase();
        return display.toLowerCase().replace(" ", "");
    }

    // -----------------------------------------------------------------------
    // Balance command helpers
    // -----------------------------------------------------------------------

    /**
     * Gets available keys at a given path in the hero config.
     * Supports list traversal by type name (e.g., "abilities.passives" returns type names).
     * Used for tab completion.
     */
    public static List<String> getConfigKeys(String heroName, String path) {
        YamlConfiguration config = getConfig(heroName);
        if (config == null) return Collections.emptyList();

        if (path.isEmpty()) {
            return new ArrayList<>(config.getKeys(false));
        }

        // Try standard config section first
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            return new ArrayList<>(section.getKeys(false));
        }

        // Try resolving through lists (by type lookup)
        Object resolved = resolveValue(config, path.split("\\."));
        return keysOf(resolved);
    }

    /**
     * Gets a leaf value from the hero config.
     * Supports list traversal by type name (e.g., "abilities.passives.bulkslam.damage").
     * Returns null for non-leaf values (sections, lists, maps).
     */
    public static String getValue(String heroName, String path) {
        YamlConfiguration config = getConfig(heroName);
        if (config == null) return null;

        Object resolved = resolveValue(config, path.split("\\."));
        if (resolved == null || resolved instanceof ConfigurationSection
                || resolved instanceof List || resolved instanceof Map) {
            return null;
        }
        return resolved.toString();
    }

    /**
     * Sets a value in the hero config and saves, then reloads THIS hero's archetype so the edit
     * is live for the next hero construction without a full /balance reload.
     * Supports list traversal by type name (e.g., "abilities.passives.bulkslam.damage").
     *
     * <p>Written via {@link YmlSurgeon} text surgery, NOT a YamlConfiguration round-trip — the
     * round-trip strips every comment that isn't attached to a config path, which is most of a
     * hero yml (inline {@code # FILL} notes and everything inside ability lists). Throws
     * {@link IllegalStateException} with the reason when the path can't be located.
     */
    public static boolean setValue(String heroName, String path, String value) {
        File file = getFile(heroName);
        if (!file.exists()) return false;

        // The path must already resolve to a scalar leaf — the surgeon walks the document by
        // first match, and a strict pre-check keeps an invalid path (typo'd level) from ever
        // landing on some deeper line that happens to share the key name.
        if (getValue(heroName, path) == null) {
            throw new IllegalStateException("'" + path + "' is not an existing value in "
                    + heroName + " — /balance can edit existing keys only");
        }

        String original;
        try {
            original = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.getName(), e);
        }

        YmlSurgeon.setScalar(file, path.split("\\."), value);
        reload(heroName);

        // Transactional verify: re-resolve the path from the edited file. A mismatch means the
        // surgery hit a different line than strict path resolution — restore and refuse.
        String now = getValue(heroName, path);
        if (now == null || !now.equalsIgnoreCase(value)) {
            try {
                Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Bad edit AND restore failed for " + file.getName(), e);
            }
            reload(heroName);
            throw new IllegalStateException("Edit verification failed ('" + path + "' reads back as "
                    + now + ") — " + file.getName() + " was restored unchanged");
        }
        return true;
    }

    /**
     * Handles the balance command logic for get/set operations.
     */
    public static boolean handleConfigCommand(Player player, String heroName, String[] pathArgs) {
        if (pathArgs.length == 0) {
            List<String> keys = getConfigKeys(heroName, "");
            player.sendMessage(Component.text("Available keys: " + String.join(", ", keys), NamedTextColor.AQUA));
            return true;
        }

        String fullPath = String.join(".", pathArgs);
        String pathWithoutLast = String.join(".", Arrays.copyOfRange(pathArgs, 0, pathArgs.length - 1));

        // Try as GET first
        String currentValue = getValue(heroName, fullPath);
        if (currentValue != null) {
            player.sendMessage(Component.text(fullPath + " = " + currentValue, NamedTextColor.GREEN));
            return true;
        }

        // Check if it's a section (show children)
        List<String> children = getConfigKeys(heroName, fullPath);
        if (!children.isEmpty()) {
            player.sendMessage(Component.text(fullPath + " children: " + String.join(", ", children), NamedTextColor.AQUA));
            return true;
        }

        // Try as SET (last arg is value)
        if (pathArgs.length >= 2) {
            String value = pathArgs[pathArgs.length - 1];
            try {
                if (setValue(heroName, pathWithoutLast, value)) {
                    player.sendMessage(Component.text("Set " + pathWithoutLast + " = " + value, NamedTextColor.GREEN));
                    return true;
                }
            } catch (IllegalStateException e) {
                // Surgery failures carry the exact reason (path not found / not a scalar /
                // would corrupt the file) — show it rather than the generic path error.
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                return true;
            }
        }

        player.sendMessage(Component.text("Path not found: " + fullPath, NamedTextColor.RED));
        return true;
    }

    // -----------------------------------------------------------------------
    // List-aware path resolution (for passives and other YAML list sections)
    // -----------------------------------------------------------------------

    // Walks a path that may traverse YAML lists by matching the "type" field.
    // e.g., ["abilities", "passives", "bulkslam", "damage"] resolves through the
    // passives list to find the entry with type=bulkslam, then returns its damage value.
    private static Object resolveValue(YamlConfiguration config, String[] segments) {
        Object current = config;
        for (String segment : segments) {
            current = navigateNext(current, segment);
            if (current == null) return null;
        }
        return current;
    }

    private static Object navigateNext(Object current, String segment) {
        if (current instanceof ConfigurationSection section) {
            return section.get(segment);
        } else if (current instanceof Map<?, ?> map) {
            return map.get(segment);
        } else if (current instanceof List<?> list) {
            return findListEntryByType(list, segment);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findListEntryByType(List<?> list, String typeName) {
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object type = map.get("type");
                if (type != null && type.toString().equalsIgnoreCase(typeName)) {
                    return (Map<String, Object>) map;
                }
            }
        }
        return null;
    }

    private static List<String> keysOf(Object obj) {
        if (obj instanceof ConfigurationSection section) {
            return new ArrayList<>(section.getKeys(false));
        }
        if (obj instanceof List<?> list) {
            List<String> types = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.containsKey("type")) {
                    types.add(map.get("type").toString());
                }
            }
            return types;
        }
        if (obj instanceof Map<?, ?> map) {
            List<String> keys = new ArrayList<>();
            for (Object key : map.keySet()) {
                keys.add(key.toString());
            }
            return keys;
        }
        return Collections.emptyList();
    }

}
