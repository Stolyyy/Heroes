package me.stolyy.heroes.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Abstract base class for all NPC types.
 * Subclasses define specific NPC behaviors (lobby display, announcer, game bot).
 */
public abstract class Npc {

    protected final String id;
    protected final Location location;
    protected NpcHandle handle;

    protected Npc(String id, Location location) {
        this.id = id;
        this.location = location.clone();
    }

    /**
     * @return the unique ID of this NPC
     */
    public String getId() {
        return id;
    }

    /**
     * @return the spawn location of this NPC
     */
    public Location getLocation() {
        return location.clone();
    }

    /**
     * @return the NPC handle, or null if not spawned
     */
    public NpcHandle getHandle() {
        return handle;
    }

    /**
     * Spawns this NPC into the world.
     * Subclasses should call the NpcService to create and configure the NPC.
     */
    public abstract void spawn();

    /**
     * Despawns this NPC from the world.
     */
    public void despawn() {
        if (handle != null) {
            NpcServiceProvider.get().despawn(handle);
            NpcServiceProvider.get().remove(id);
            handle = null;
        }
    }

    /**
     * Handles a player interaction with this NPC.
     * @param player the player who interacted
     */
    public abstract void handleInteraction(Player player);

    /**
     * Handles a RIGHT-click on this NPC. Defaults to the primary interaction; NPC types
     * with a distinct right-click action (hero NPCs open the info sheet) override this.
     * Left-clicks route to {@link #handleInteraction}.
     */
    public void handleSecondaryInteraction(Player player) {
        handleInteraction(player);
    }
}
