package me.stolyy.heroes.vfx.glow;

import fr.skytasul.glowingentities.GlowingEntities;
import me.stolyy.heroes.Heroes;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * GlowingEntities-backed {@link GlowService}. The library is shaded into the plugin jar (relocated to
 * me.stolyy.heroes.libs.glowingentities at build time), so no server-side plugin install is needed.
 */
public class GlowingEntitiesGlowService implements GlowService {

    private GlowingEntities glowingEntities;

    @Override
    public boolean isAvailable() {
        return glowingEntities != null;
    }

    @Override
    public void enable() {
        // The GlowingEntities constructor enables itself, and calling enable() on an already-enabled
        // instance throws — so construction IS the enable, and a second call must be a no-op.
        if (glowingEntities != null) return;
        glowingEntities = new GlowingEntities(Heroes.getInstance());
    }

    @Override
    public void disable() {
        if (glowingEntities == null) return;
        glowingEntities.disable();
        glowingEntities = null;
    }

    @Override
    public void glow(Entity target, Player viewer) {
        try {
            // The colour-less overload passes color == null, and the library then sends ONLY the
            // metadata glow flag — no scoreboard-team packets in either direction. That is load-bearing:
            // its colour teams hold player-name entries, the same entries the game's real hg_ nametag
            // teams own, and the two systems stealing entries from each other is what produced the
            // Network Protocol Error kicks (the client throws on a team entry-remove for an entry that
            // was stolen by the other side). The outline colour comes from the hg_ team instead —
            // always the team colour, always agreeing with the nametag.
            glowingEntities.setGlowing(target, viewer);
        } catch (ReflectiveOperationException e) {
            throw failure("outline " + target.getName() + " for " + viewer.getName(), e);
        }
    }

    @Override
    public void clear(int entityId, Player viewer) {
        try {
            glowingEntities.unsetGlowing(entityId, viewer);
        } catch (ReflectiveOperationException e) {
            throw failure("clear outline " + entityId + " for " + viewer.getName(), e);
        }
    }

    // The library's whole API is checked-exception based because every call is reflective NMS.
    // Rethrown unchecked rather than swallowed: a failure here means the library no longer matches
    // the server build, which has to be visible rather than degrade into silently missing outlines.
    private IllegalStateException failure(String what, ReflectiveOperationException cause) {
        return new IllegalStateException("GlowingEntities failed to " + what, cause);
    }
}
