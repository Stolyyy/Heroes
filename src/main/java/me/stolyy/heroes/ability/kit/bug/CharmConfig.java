package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.ability.kit.bug.charms.Charm;
import me.stolyy.heroes.ability.kit.bug.charms.SoulCharm;
import me.stolyy.heroes.ability.kit.bug.charms.StatCharm;
import me.stolyy.heroes.ability.kit.bug.charms.StatTarget;
import me.stolyy.heroes.ability.kit.bug.charms.Weaversong;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.util.Configs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class CharmConfig {
    private static int notches;

    private static final Map<Charms, Integer> costs = new EnumMap<>(Charms.class);
    private static final Map<Charms, String> descriptions = new EnumMap<>(Charms.class);
    private static final Map<Charms, String> textures = new EnumMap<>(Charms.class);
    private static final Map<Charms, Supplier<Charm>> factories = new EnumMap<>(Charms.class);

    public static void load() {
        YamlConfiguration config = HeroConfig.getConfig("bug");
        if (config == null) {
            throw new IllegalStateException("Missing loaded hero config 'bug' for CharmConfig");
        }
        ConfigurationSection charmsSec = Configs.requireSection(config, "charms");
        int loadedNotches = Configs.requireInt(charmsSec, "notches");

        Map<Charms, Integer> loadedCosts = new EnumMap<>(Charms.class);
        Map<Charms, String> loadedDescriptions = new EnumMap<>(Charms.class);
        Map<Charms, String> loadedTextures = new EnumMap<>(Charms.class);
        Map<Charms, Supplier<Charm>> loadedFactories = new EnumMap<>(Charms.class);

        for (Charms charm : Charms.values()) {
            String key = charm.name().toLowerCase(Locale.ROOT);
            ConfigurationSection sec = Configs.requireSection(charmsSec, key);

            loadedCosts.put(charm, Configs.requireInt(sec, "cost"));
            loadedDescriptions.put(charm, Configs.requireString(sec, "description"));
            loadedTextures.put(charm, sec.contains("texture")
                    ? Configs.requireString(sec, "texture")
                    : key.replace('_', '-'));

            if (charm == Charms.WEAVERSONG) {
                Configs.requireInt(sec, "count");
                Configs.requireDouble(sec, "respawn_delay");
                Configs.requireSection(sec, "minion");
                loadedFactories.put(charm, () -> new Weaversong(sec));
            } else if (sec.contains("modifiers")) {
                List<StatCharm.ModifierEntry> entries = parseModifiers(key, sec);
                loadedFactories.put(charm, () -> new StatCharm(charm, entries));
            } else if (sec.contains("soul")) {
                ConfigurationSection soulSec = Configs.requireSection(sec, "soul");
                String triggerStr = Configs.requireString(soulSec, "trigger").toUpperCase(Locale.ROOT);
                SoulCharm.Trigger trigger = SoulCharm.Trigger.valueOf(triggerStr);
                double value = Configs.requireDouble(soulSec, "value");
                loadedFactories.put(charm, () -> new SoulCharm(trigger, value));
            } else {
                throw new IllegalStateException("Charm 'charms." + key + "' must define 'modifiers' or 'soul'");
            }
        }

        notches = loadedNotches;
        costs.clear();
        costs.putAll(loadedCosts);
        descriptions.clear();
        descriptions.putAll(loadedDescriptions);
        textures.clear();
        textures.putAll(loadedTextures);
        factories.clear();
        factories.putAll(loadedFactories);
    }

    private static List<StatCharm.ModifierEntry> parseModifiers(String charmKey, ConfigurationSection sec) {
        List<?> raw = sec.getList("modifiers");
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("Charm 'charms." + charmKey + ".modifiers' must be a non-empty list");
        }
        List<StatCharm.ModifierEntry> entries = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object item = raw.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Charm 'charms." + charmKey + ".modifiers[" + i + "]' must be a map");
            }
            Object targetRaw = map.get("target");
            if (targetRaw == null) {
                throw new IllegalStateException("Charm 'charms." + charmKey + ".modifiers[" + i + "]' missing 'target'");
            }
            StatTarget target = StatTarget.valueOf(targetRaw.toString().toUpperCase(Locale.ROOT));

            boolean hasMult = map.containsKey("mult");
            boolean hasAdd = map.containsKey("add");
            if (hasMult == hasAdd) {
                throw new IllegalStateException("Charm 'charms." + charmKey + ".modifiers[" + i
                        + "]' must specify exactly one of 'mult' or 'add'");
            }
            Object valueRaw = hasMult ? map.get("mult") : map.get("add");
            if (!(valueRaw instanceof Number num)) {
                throw new IllegalStateException("Charm 'charms." + charmKey + ".modifiers[" + i
                        + "]' operator value must be numeric");
            }
            entries.add(new StatCharm.ModifierEntry(
                    target, hasMult ? StatTarget.Op.MULT : StatTarget.Op.ADD, num.doubleValue()));
        }
        return List.copyOf(entries);
    }

    public static Charm createCharm(Charms charm) {
        Supplier<Charm> factory = factories.get(charm);
        if (factory == null) throw new IllegalStateException("CharmConfig not loaded for " + charm);
        return factory.get();
    }

    public static int notches() {
        return notches;
    }

    public static int getCost(Charms charm) {
        Integer cost = costs.get(charm);
        if (cost == null) throw new IllegalStateException("CharmConfig not loaded for " + charm);
        return cost;
    }

    public static String getDescription(Charms charm) {
        String desc = descriptions.get(charm);
        if (desc == null) throw new IllegalStateException("CharmConfig not loaded for " + charm);
        return desc;
    }

    public static String getTexture(Charms charm) {
        String texture = textures.get(charm);
        if (texture == null) throw new IllegalStateException("CharmConfig not loaded for " + charm);
        return texture.replace('_', '-');
    }
}
