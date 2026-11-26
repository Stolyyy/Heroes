package me.stolyy.heroes.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Parses single-or-list YAML spec blocks into isolated sections without mutating loaded configs. */
public final class SpecList {
    private SpecList() {}

    /** Builds a standalone {@link ConfigurationSection} backed by a fresh {@link MemoryConfiguration}. */
    public static ConfigurationSection fromMap(Map<?, ?> map) {
        MemoryConfiguration section = new MemoryConfiguration();
        populateMap(section, map);
        return section;
    }

    private static void populateMap(ConfigurationSection section, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                populateMap(section.createSection(key), nested);
            } else {
                section.set(key, value);
            }
        }
    }

    /**
     * Parses either {@code config.<listKey>} (when present as a YAML list of maps) or {@code config}
     * itself as a single-entry list.
     */
    public static <T> List<T> parse(ConfigurationSection config, String listKey,
                                    Function<ConfigurationSection, T> parser) {
        if (!config.contains(listKey)) {
            return List.of(parser.apply(config));
        }
        List<?> list = config.getList(listKey);
        if (list == null) {
            throw new IllegalStateException("Key '" + listKey + "' at '" + config.getCurrentPath() + "' must be a list");
        }
        List<T> parsed = new ArrayList<>(list.size());
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Entry in '" + listKey + "' at '"
                        + config.getCurrentPath() + "' must be a YAML map, got: " + obj);
            }
            parsed.add(parser.apply(fromMap(map)));
        }
        return List.copyOf(parsed);
    }
}
