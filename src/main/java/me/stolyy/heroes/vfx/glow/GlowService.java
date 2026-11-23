package me.stolyy.heroes.vfx.glow;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Per-viewer entity outlines.
 *
 * <p>Bukkit's {@link Entity#setGlowing(boolean)} is a broadcast metadata flag: everyone who can see the
 * entity sees the outline. A teammate-only outline is therefore impossible through the API alone — it
 * needs the metadata flag sent to one player by packet.
 *
 * <p>The outline is METADATA-ONLY, deliberately carrying no colour. The vanilla client colours a glow
 * from the scoreboard team the entity's name is on, and every game participant already sees the game's
 * real nametag teams ({@code hg_<COLOR>} in GameVisuals) — so the outline picks up the correct team
 * colour for free. Sending colour the library way (packet-only fake teams holding the same name entries)
 * fights the real teams for entry ownership, and the loser's stale entry-remove packet crashes the
 * client with a Network Protocol Error kick — the vanilla client throws on removing an entry from a
 * team it is no longer on.
 *
 * <p>The indirection exists so that packet backend is replaceable. The bundled GlowingEntities library
 * reaches into NMS by reflection against a mappings file, which is exactly the kind of thing that breaks
 * on a Minecraft update; when it does, only {@link GlowingEntitiesGlowService} has to be rewritten (or
 * temporarily swapped for {@link NoOpGlowService}) and nothing in the game layer moves.
 *
 * <p>Implementations are packet primitives only. All bookkeeping — who is outlined for whom, roster
 * diffing, the config gate — lives in {@link TeamGlow}, so a replacement backend stays small.
 */
public interface GlowService {

    /**
     * @return true if the backend is live and outlines will actually be sent
     */
    boolean isAvailable();

    /**
     * Starts the backend. Called once at plugin enable, before any glow call.
     */
    void enable();

    /**
     * Stops the backend and releases its packet handlers. Called at plugin disable.
     */
    void disable();

    /**
     * Outlines {@code target}, visible to {@code viewer} only. The colour comes from the scoreboard
     * team the target is already on for that viewer (the game's nametag team). Repeat calls are cheap.
     */
    void glow(Entity target, Player viewer);

    /**
     * Removes an outline for one viewer.
     *
     * <p>Takes the entity id rather than the {@link Entity} because clearing must still work once the
     * entity is gone (bot despawned, player logged out) — undoing the packets needs nothing but the id.
     */
    void clear(int entityId, Player viewer);
}
