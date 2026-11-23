package me.stolyy.heroes.control;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.UUID;

public interface Controller {
    LivingEntity getEntity();
    UUID getUniqueId();
    Location getLocation();
    Location getEyeLocation();
    Location getCenter();
    void setAllowFlight(boolean allow);
    boolean isOnGround();
    BoundingBox getBoundingBox();
    Vector getVelocity();
    
    boolean isPlayer();
    boolean isSneaking();
    
    // Actions
    void sendMessage(Component message);
    void sendActionBar(Component message);
    void sendTitle(Component title, Component subtitle);
    void sendTitle(Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks);
    void setVelocity(Vector velocity);
    void playSound(Object sound, float volume, float pitch);

    /**
     * Moves the controlled entity. Implementations route this through whatever owns the entity's
     * position bookkeeping, so callers must never reach for the raw entity to teleport.
     */
    void teleport(Location location);

    /**
     * Restores current health to the entity's max-health attribute value.
     */
    void healToFull();

    // Equipment
    void setInventoryItem(int slot, ItemStack item);
    void setHelmet(ItemStack item);
    void setChestplate(ItemStack item);
    void setLeggings(ItemStack item);
    void setBoots(ItemStack item);

    // Visuals
    void updateEnergyDisplay(float percent, int value);
    
    /**
     * Updates the visual cooldown on the hotbar item.
     * @param slotIndex 0=Primary, 1=Secondary, 2=Smash
     * @param progress 0.0 (Ready) to 1.0 (Cooldown)
     */
    void updateItemCooldown(int slotIndex, float progress);
}
