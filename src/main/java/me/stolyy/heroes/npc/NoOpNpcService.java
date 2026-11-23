package me.stolyy.heroes.npc;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/**
 * No-op implementation of NpcService for when no NPC library is available.
 * All methods are safe no-ops or return null/false.
 */
public class NoOpNpcService implements NpcService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public NpcHandle create(String id, Location location) {
        return null;
    }

    @Override
    public void remove(String id) {
        // No-op
    }

    @Override
    public void removeAll() {
        // No-op
    }

    @Override
    public void setDisplayName(NpcHandle handle, String displayName) {
        // No-op
    }

    @Override
    public void setSkin(NpcHandle handle, String skinValue, String signature) {
        // No-op
    }

    @Override
    public void setEquipment(NpcHandle handle, ItemStack helmet, ItemStack chest,
                             ItemStack legs, ItemStack boots, ItemStack mainHand) {
        // No-op
    }

    @Override
    public void setShowInTab(NpcHandle handle, boolean show) {
        // No-op
    }

    @Override
    public void setNameplateVisible(NpcHandle handle, boolean visible) {
        // No-op
    }

    @Override
    public void setTurnToPlayer(NpcHandle handle, boolean turn) {
        // No-op
    }

    @Override
    public void setProtected(NpcHandle handle, boolean protectedFlag) {
        // No-op
    }

    @Override
    public void spawn(NpcHandle handle) {
        // No-op
    }

    @Override
    public void despawn(NpcHandle handle) {
        // No-op
    }

    @Override
    public void teleport(NpcHandle handle, Location location) {
        // No-op
    }

    @Override
    public void lookAt(NpcHandle handle, Location target) {
        // No-op
    }

    @Override
    public void navigateTo(NpcHandle handle, Location target) {
        // No-op
    }

    @Override
    public void navigateToEntity(NpcHandle handle, LivingEntity target, boolean aggressive) {
        // No-op
    }

    @Override
    public void stopNavigation(NpcHandle handle) {
        // No-op
    }

    @Override
    public boolean isNavigating(NpcHandle handle) {
        return false;
    }

    @Override
    public void setNavigationSpeed(NpcHandle handle, float speed) {
        // No-op
    }

    @Override
    public LivingEntity getBukkitEntity(NpcHandle handle) {
        return null;
    }

    @Override
    public boolean isNpcEntity(org.bukkit.entity.Entity entity) {
        return false;
    }
}
