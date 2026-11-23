package me.stolyy.heroes.npc;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/**
 * Interface for NPC operations.
 * Implementations handle the actual NPC library (Citizens, etc.)
 */
public interface NpcService {

    /**
     * @return true if the NPC library is available and functional
     */
    boolean isAvailable();

    /**
     * Creates a new NPC at the given location.
     * @param id unique identifier for this NPC
     * @param location spawn location
     * @return handle to the created NPC, or null if creation failed
     */
    NpcHandle create(String id, Location location);

    /**
     * Removes an NPC by its ID.
     */
    void remove(String id);

    /**
     * Removes all NPCs managed by this service.
     */
    void removeAll();

    /**
     * Sets the display name shown above the NPC.
     */
    void setDisplayName(NpcHandle handle, String displayName);

    /**
     * Sets the NPC's skin using Mojang skin data.
     * @param skinValue base64 skin texture value
     * @param signature base64 signature
     */
    void setSkin(NpcHandle handle, String skinValue, String signature);

    /**
     * Sets the NPC's equipment.
     */
    void setEquipment(NpcHandle handle, ItemStack helmet, ItemStack chest,
                      ItemStack legs, ItemStack boots, ItemStack mainHand);

    /**
     * Sets whether the NPC appears in the tab list.
     */
    void setShowInTab(NpcHandle handle, boolean show);

    /**
     * Sets whether the NPC's overhead nameplate is visible.
     */
    void setNameplateVisible(NpcHandle handle, boolean visible);

    /**
     * Sets whether the NPC turns to face nearby players.
     */
    void setTurnToPlayer(NpcHandle handle, boolean turn);

    /**
     * Sets whether the NPC is protected. Protected NPCs (the library default) ignore incoming
     * damage and external movement/knockback, so combat NPCs (minions, game bots) must set this
     * to {@code false} to be damageable and movable.
     */
    void setProtected(NpcHandle handle, boolean protectedFlag);

    /**
     * Spawns the NPC in the world.
     */
    void spawn(NpcHandle handle);

    /**
     * Despawns the NPC from the world.
     */
    void despawn(NpcHandle handle);

    /**
     * Teleports the NPC to a new location.
     */
    void teleport(NpcHandle handle, Location location);

    /**
     * Makes the NPC look at a specific location.
     */
    void lookAt(NpcHandle handle, Location target);

    // --- Navigation (Citizens2) ---

    /**
     * Navigates the NPC to a target location.
     */
    void navigateTo(NpcHandle handle, Location target);

    /**
     * Navigates the NPC toward a living entity.
     * @param aggressive if true, the NPC will attack the target on arrival
     */
    void navigateToEntity(NpcHandle handle, LivingEntity target, boolean aggressive);

    /**
     * Kiting facing (V2 §7): while set, navigation carries a per-tick look override so the NPC
     * faces {@code lookTarget} even while pathing elsewhere. Citizens implements this as a
     * packet-level rotation rewrite — it REQUIRES ProtocolLib to be visible and is a harmless
     * cosmetic no-op without it. Server-side yaw still belongs to navigation either way.
     * Null clears the override.
     */
    default void setNavigationLookAt(NpcHandle handle, java.util.function.Supplier<Location> lookTarget) {}

    /**
     * Stops any active navigation for this NPC.
     */
    void stopNavigation(NpcHandle handle);

    /**
     * @return true if the NPC is currently navigating
     */
    boolean isNavigating(NpcHandle handle);

    /**
     * Sets the NPC's navigation movement speed.
     */
    void setNavigationSpeed(NpcHandle handle, float speed);

    /**
     * Gets the underlying Bukkit entity for this NPC.
     * @return the LivingEntity, or null if not spawned
     */
    LivingEntity getBukkitEntity(NpcHandle handle);

    /**
     * @return true if this entity is NPC-backed. Asks the NPC library's registry directly rather
     * than reading entity metadata, which the library strips on death.
     */
    boolean isNpcEntity(org.bukkit.entity.Entity entity);
}
