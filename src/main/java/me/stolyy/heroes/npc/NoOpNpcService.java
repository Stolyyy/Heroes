package me.stolyy.heroes.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/** Fallback {@link NpcService} when no NPC provider plugin is loaded. */
public class NoOpNpcService implements NpcService {

    @Override public boolean isAvailable() { return false; }
    @Override public NpcHandle create(String id, Location location) { return null; }
    @Override public void remove(String id) {}
    @Override public void removeAll() {}
    @Override public void setDisplayName(NpcHandle handle, String displayName) {}
    @Override public void setSkin(NpcHandle handle, String skinValue, String signature) {}
    @Override public void setEquipment(NpcHandle handle, ItemStack helmet, ItemStack chest,
                                       ItemStack legs, ItemStack boots, ItemStack mainHand) {}
    @Override public void setShowInTab(NpcHandle handle, boolean show) {}
    @Override public void setNameplateVisible(NpcHandle handle, boolean visible) {}
    @Override public void setTurnToPlayer(NpcHandle handle, boolean turn) {}
    @Override public void setProtected(NpcHandle handle, boolean protectedFlag) {}
    @Override public void spawn(NpcHandle handle) {}
    @Override public void despawn(NpcHandle handle) {}
    @Override public void teleport(NpcHandle handle, Location location) {}
    @Override public void lookAt(NpcHandle handle, Location target) {}
    @Override public void navigateTo(NpcHandle handle, Location target) {}
    @Override public void navigateToEntity(NpcHandle handle, LivingEntity target, boolean aggressive) {}
    @Override public void stopNavigation(NpcHandle handle) {}
    @Override public boolean isNavigating(NpcHandle handle) { return false; }
    @Override public void setNavigationSpeed(NpcHandle handle, float speed) {}
    @Override public LivingEntity getBukkitEntity(NpcHandle handle) { return null; }
    @Override public boolean isNpcEntity(Entity entity) { return false; }
}
