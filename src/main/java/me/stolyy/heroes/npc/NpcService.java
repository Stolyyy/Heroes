package me.stolyy.heroes.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

/** Interface for NPC operations backed by an external NPC provider (e.g. Citizens). */
public interface NpcService {

    boolean isAvailable();

    NpcHandle create(String id, Location location);

    void remove(String id);

    void removeAll();

    void setDisplayName(NpcHandle handle, String displayName);

    void setSkin(NpcHandle handle, String skinValue, String signature);

    void setEquipment(NpcHandle handle, ItemStack helmet, ItemStack chest,
                      ItemStack legs, ItemStack boots, ItemStack mainHand);

    void setShowInTab(NpcHandle handle, boolean show);

    void setNameplateVisible(NpcHandle handle, boolean visible);

    void setTurnToPlayer(NpcHandle handle, boolean turn);

    /**
     * Protected NPCs ignore incoming damage and external knockback; combat NPCs must set this
     * to {@code false}.
     */
    void setProtected(NpcHandle handle, boolean protectedFlag);

    void spawn(NpcHandle handle);

    void despawn(NpcHandle handle);

    void teleport(NpcHandle handle, Location location);

    void lookAt(NpcHandle handle, Location target);

    void navigateTo(NpcHandle handle, Location target);

    void navigateToEntity(NpcHandle handle, LivingEntity target, boolean aggressive);

    /**
     * Kiting facing override while navigating (null clears). Packet-level under Citizens and
     * requires PacketEvents to be visible client-side.
     */
    default void setNavigationLookAt(NpcHandle handle, Supplier<Location> lookTarget) {}

    void stopNavigation(NpcHandle handle);

    boolean isNavigating(NpcHandle handle);

    /**
     * Sets navigation movement speed as a multiple of the NPC's own {@code MOVEMENT_SPEED} base
     * attribute ({@code 1.0} = walk parity, {@code 1.3} = sprint parity). Implementations invert
     * library-specific movement scaling so {@code speed} keeps this linear unit contract.
     */
    void setNavigationSpeed(NpcHandle handle, float speed);

    LivingEntity getBukkitEntity(NpcHandle handle);

    boolean isNpcEntity(Entity entity);
}
