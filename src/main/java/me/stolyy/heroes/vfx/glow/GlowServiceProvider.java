package me.stolyy.heroes.vfx.glow;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;

/**
 * Factory for the active {@link GlowService}. Reads the game-config.yml toggle once and picks the
 * backend, mirroring {@link me.stolyy.heroes.npc.NpcServiceProvider}.
 */
public class GlowServiceProvider {

    private static GlowService instance;

    public static void initialize() {
        if (instance == null) {
            instance = createService();
        }
    }

    public static GlowService get() {
        if (instance == null) {
            throw new IllegalStateException("GlowServiceProvider is not initialized (or has already shut down)");
        }
        return instance;
    }

    public static boolean isAvailable() {
        return instance != null && instance.isAvailable();
    }

    private static GlowService createService() {
        if (!Configs.requireBoolean(GameConfigManager.getGameConfig(), "teammate-glow.enabled")) {
            Heroes.getInstance().getLogger().info("Teammate glow disabled in game-config.yml");
            return new NoOpGlowService();
        }

        GlowService service = new GlowingEntitiesGlowService();
        try {
            service.enable();
        } catch (IllegalStateException | NoClassDefFoundError e) {
            // GlowingEntities resolves NMS by reflection at first construction, so an unsupported server
            // build fails HERE, loudly, at startup rather than mid-match. Falling back keeps every other
            // system running on a Minecraft update that outruns the library — the reason the interface exists.
            Heroes.getInstance().getLogger().warning(
                    "Teammate glow unavailable - GlowingEntities failed to initialise: " + e);
            return new NoOpGlowService();
        }
        Heroes.getInstance().getLogger().info("Teammate glow enabled (GlowingEntities)");
        return service;
    }

    /**
     * Stops the backend and forgets it. {@link #isAvailable()} returns false afterwards and
     * {@link #get()} throws until {@link #initialize()} is called again.
     */
    public static void shutdown() {
        if (instance == null) return;
        instance.disable();
        instance = null;
    }
}
