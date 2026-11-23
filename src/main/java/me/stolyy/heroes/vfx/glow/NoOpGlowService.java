package me.stolyy.heroes.vfx.glow;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Used when the feature is switched off in game-config.yml, or when the packet backend failed to
 * initialise against this server build. {@link #isAvailable()} is false, so {@link TeamGlow} short-circuits
 * before it ever tracks state.
 */
public class NoOpGlowService implements GlowService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void enable() {
        // No-op
    }

    @Override
    public void disable() {
        // No-op
    }

    @Override
    public void glow(Entity target, Player viewer) {
        // No-op
    }

    @Override
    public void clear(int entityId, Player viewer) {
        // No-op
    }
}
