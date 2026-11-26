package me.stolyy.heroes.control;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Body abstraction shared by human players ({@link PlayerController}) and NPC bots
 * ({@link BotController}), routing movement, equipment, and HUD feedback.
 */
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
    void setSneaking(boolean sneaking);
    void setSprinting(boolean sprinting);
    void swingMainHand();
    void setFallDistance(float distance);
    boolean isUseHeld();
    boolean isDrawingBow();
    Vector getMovementAxes();

    void addPassenger(Entity passenger);
    void removePassenger(Entity passenger);
    boolean hasPassenger(Entity passenger);
    void mount(Entity vehicle);
    void leaveVehicle();
    void addPotionEffect(PotionEffect effect);
    void removePotionEffect(PotionEffectType type);
    void setGameMode(GameMode mode);

    // Player-facing notifications and HUD updates; intentional no-ops on bot controllers.
    void sendMessage(Component message);
    void sendActionBar(Component message);
    void sendTitle(Component title, Component subtitle);
    void sendTitle(Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks);
    void setVelocity(Vector velocity);

    /**
     * Moves the controlled entity through controller-managed position bookkeeping so callers
     * never invoke raw {@code Entity.teleport}.
     */
    void teleport(Location location);

    /** Restores current health to the entity's max-health attribute value. */
    void healToFull();

    ItemStack getInventoryItem(int slot);
    void setInventoryItem(int slot, ItemStack item);
    void setHelmet(ItemStack item);
    void setChestplate(ItemStack item);
    void setLeggings(ItemStack item);
    void setBoots(ItemStack item);

    void updateEnergyDisplay(float percent, int value);

    /**
     * Updates visual cooldown progress on the hotbar item.
     *
     * @param slotIndex 0=Primary, 1=Secondary, 2=Smash
     * @param progress  0.0 (ready) to 1.0 (on cooldown)
     */
    void updateItemCooldown(int slotIndex, float progress);
}
