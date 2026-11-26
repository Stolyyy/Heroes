package me.stolyy.heroes.vfx.effect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

/** Shared PDC keys and orphan sweeps for plugin-owned display entities. */
public final class DisplayTags {
    public static final NamespacedKey VFX_DISPLAY = new NamespacedKey("heroes", "vfx_display");
    public static final NamespacedKey LEADERBOARD = new NamespacedKey("heroes", "leaderboard");
    public static final NamespacedKey LEVEL_LEADERBOARD = new NamespacedKey("heroes", "level_leaderboard");
    public static final NamespacedKey MEMORIAL = new NamespacedKey("heroes", "memorial");
    public static final NamespacedKey HERO_LEVEL_TAG = new NamespacedKey("heroes", "hero_level_tag");
    public static final NamespacedKey QUEUE_NPC_TEXT = new NamespacedKey("heroes", "queue_npc_text");

    private DisplayTags() {}

    public static void tag(Entity entity, NamespacedKey key) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isPluginOwned(Entity entity) {
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            if ("heroes".equals(key.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    /** Removes every loaded entity carrying {@code key}. */
    public static void sweep(NamespacedKey key) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Purges untagged TextDisplay and Interaction entities near {@code location} left behind by builds
     * that predated PDC tagging, while sparing any entity tagged under the {@code heroes:} namespace.
     */
    public static void sweepUntaggedNear(Location location, double rx, double ry, double rz) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot sweep untagged displays with null world at " + location);
        }
        for (Entity entity : world.getNearbyEntities(location, rx, ry, rz)) {
            if (isPluginOwned(entity)) continue;
            if (entity instanceof TextDisplay || entity instanceof Interaction) {
                entity.remove();
            }
        }
    }
}
