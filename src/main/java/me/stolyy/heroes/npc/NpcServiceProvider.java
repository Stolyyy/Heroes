package me.stolyy.heroes.npc;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;

/**
 * Factory for obtaining the appropriate NpcService implementation.
 * Detects available NPC libraries and returns the correct implementation.
 */
public class NpcServiceProvider {

    private static NpcService instance;

    /**
     * Gets the NpcService instance, creating it if necessary.
     * @return the NpcService implementation
     */
    public static NpcService get() {
        if (instance == null) {
            instance = createService();
        }
        return instance;
    }

    /**
     * @return true if an NPC library is available
     */
    public static boolean isAvailable() {
        return get().isAvailable();
    }

    /**
     * Creates the appropriate NpcService based on available plugins.
     */
    private static NpcService createService() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            try {
                Heroes.getInstance().getLogger().info("Citizens detected - enabling NPC features");
                return new CitizensNpcService();
            } catch (NoClassDefFoundError e) {
                Heroes.getInstance().getLogger().warning("Citizens plugin found but classes not accessible");
            }
        }

        Heroes.getInstance().getLogger().info("No NPC library found - NPC features disabled");
        return new NoOpNpcService();
    }

    /**
     * Resets the service instance (for reloading).
     */
    public static void reset() {
        instance = null;
    }
}
