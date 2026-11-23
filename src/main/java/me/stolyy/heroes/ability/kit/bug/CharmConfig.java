package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.Heroes;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CharmConfig {
    private static YamlConfiguration config;
    private static int notches = 9;

    private static final Map<Charms, Integer> costs = new HashMap<>();
    private static final Map<Charms, String> descriptions = new HashMap<>();
    private static final Map<Charms, String> textures = new HashMap<>();

    public static void load() {
        File file = new File(Heroes.getInstance().getDataFolder(), "heroes/bug.yml");
        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
            notches = config.getInt("charms.notches", 9);

            // Pre-load all charm data
            for (Charms charm : Charms.values()) {
                String key = charm.name().toLowerCase();
                costs.put(charm, config.getInt("charms." + key + "_cost", getDefaultCost(charm)));
                descriptions.put(charm, config.getString("charms." + key + "_description", getDefaultDescription(charm)));
                textures.put(charm, config.getString("charms." + key + "_texture", key.replace('_', '-')));
            }
        } else {
            // Use defaults if no config exists
            for (Charms charm : Charms.values()) {
                costs.put(charm, getDefaultCost(charm));
                descriptions.put(charm, getDefaultDescription(charm));
                textures.put(charm, charm.name().toLowerCase().replace('_', '-'));
            }
        }
    }

    public static int notches() {
        return notches;
    }

    public static int getCost(Charms charm) {
        return costs.getOrDefault(charm, 1);
    }

    public static String getDescription(Charms charm) {
        return descriptions.getOrDefault(charm, "No description");
    }

    public static String getTexture(Charms charm) {
        return textures.getOrDefault(charm, charm.name().toLowerCase()).replace('_', '-');
    }

    private static int getDefaultCost(Charms charm) {
        return switch (charm) {
            case DASHMASTER -> 2;
            case GRUBSONG -> 1;
            case HEAVY_BLOW -> 1;
            case KINGSOUL -> 3;
            case MARK_OF_PRIDE -> 2;
            case NAIL_MASTER -> 2;
            case QUICK_SLASH -> 2;
            case SHAMAN_STONE -> 2;
            case SHARP_SHADOW -> 2;
            case SOUL_EATER -> 2;
            case SOUL_TWISTER -> 1;
            case SPRINTMASTER -> 1;
            case STEADY_BODY -> 1;
            case STRENGTH -> 2;
            case WEAVERSONG -> 3;
        };
    }

    private static String getDefaultDescription(Charms charm) {
        return switch (charm) {
            case DASHMASTER -> "Faster Dash Cooldown";
            case GRUBSONG -> "Get soul when hit";
            case HEAVY_BLOW -> "Increases knockback on jabs and the dash";
            case KINGSOUL -> "Passively gain soul every second";
            case MARK_OF_PRIDE -> "Increase jab length";
            case NAIL_MASTER -> "Get soul when you land your primary";
            case QUICK_SLASH -> "Reduce jab cooldown";
            case SHAMAN_STONE -> "Increase secondary radius, damage, and knockback";
            case SHARP_SHADOW -> "Increase primary length and damage";
            case SOUL_EATER -> "More soul on jab hit";
            case SOUL_TWISTER -> "Less soul per secondary cast";
            case SPRINTMASTER -> "Faster movement speed and 1 step height";
            case STEADY_BODY -> "More weight";
            case STRENGTH -> "More jab damage";
            case WEAVERSONG -> "Two permanent spooder buddy minions";
        };
    }
}
