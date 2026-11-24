package me.stolyy.heroes.control;

import me.stolyy.heroes.hero.AbilityListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class PlayerController implements Controller {
    private final Player player;

    public PlayerController(Player player) {
        this.player = player;
    }

    @Override
    public LivingEntity getEntity() {
        return player.isOnline() ? player : null;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public Location getLocation() {
        return player.getLocation();
    }

    @Override
    public Location getEyeLocation() { return player.getEyeLocation(); }

    @Override
    public Location getCenter() { return player.getBoundingBox().getCenter().toLocation(player.getWorld()); }

    @Override
    public void setAllowFlight(boolean allow) { player.setAllowFlight(allow); }

    @Override
    public boolean isOnGround() { return player.isOnGround(); }

    @Override
    public BoundingBox getBoundingBox() { return player.getBoundingBox(); }

    @Override
    public Vector getVelocity() { return player.getVelocity(); }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public boolean isSneaking() {
        return player.isSneaking();
    }

    @Override
    public void setSneaking(boolean sneaking) {
        player.setSneaking(sneaking);
    }

    @Override
    public void setSprinting(boolean sprinting) {
        if (player.isSprinting() != sprinting) {
            player.setSprinting(sprinting);
        }
    }

    @Override
    public void swingMainHand() {
        player.swingMainHand();
    }

    @Override
    public void setFallDistance(float distance) {
        player.setFallDistance(distance);
    }

    @Override
    public boolean isUseHeld() {
        return AbilityListener.isUseHeld(player.getUniqueId());
    }

    @Override
    public boolean isDrawingBow() {
        return player.isHandRaised()
                && player.getInventory().getItemInMainHand().getType() == Material.BOW;
    }

    @Override
    public Vector getMovementAxes() {
        Input input = player.getCurrentInput();
        int side = (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0);
        int forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        return new Vector(side, 0, forward);
    }

    @Override public void addPassenger(Entity passenger) { player.addPassenger(passenger); }
    @Override public void removePassenger(Entity passenger) { player.removePassenger(passenger); }
    @Override public boolean hasPassenger(Entity passenger) { return player.getPassengers().contains(passenger); }
    @Override public void mount(Entity vehicle) { vehicle.addPassenger(player); }
    @Override public void leaveVehicle() { player.leaveVehicle(); }
    @Override public void addPotionEffect(PotionEffect effect) { player.addPotionEffect(effect); }
    @Override public void removePotionEffect(PotionEffectType type) { player.removePotionEffect(type); }
    @Override public void setGameMode(GameMode mode) { player.setGameMode(mode); }

    @Override
    public void sendMessage(Component message) {
        player.sendMessage(message);
    }

    @Override
    public void sendActionBar(Component message) {
        player.sendActionBar(message);
    }

    @Override
    public void sendTitle(Component title, Component subtitle) {
        player.showTitle(Title.title(title, subtitle));
    }

    @Override
    public void sendTitle(Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L));
        player.showTitle(Title.title(title, subtitle, times));
    }

    @Override
    public void setVelocity(Vector velocity) {
        player.setVelocity(velocity);
    }

    @Override
    public void teleport(Location location) {
        player.teleport(location);
    }

    @Override
    public void healToFull() {
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
    }

    @Override
    public void updateEnergyDisplay(float percent, int value) {
        player.setLevel(value);
        player.setExp(Math.max(0, Math.min(1, percent)));
    }

    @Override
    public void updateItemCooldown(int slotIndex, float progress) {
        ItemStack item = player.getInventory().getItem(slotIndex);
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int max = item.getType().getMaxDurability();
            damageable.setDamage((int) (max * progress));
            item.setItemMeta(meta);
        }
    }

    @Override public ItemStack getInventoryItem(int slot) { return player.getInventory().getItem(slot); }
    @Override public void setInventoryItem(int slot, ItemStack item) { player.getInventory().setItem(slot, item); }
    @Override public void setHelmet(ItemStack item) { player.getInventory().setHelmet(item); }
    @Override public void setChestplate(ItemStack item) { player.getInventory().setChestplate(item); }
    @Override public void setLeggings(ItemStack item) { player.getInventory().setLeggings(item); }
    @Override public void setBoots(ItemStack item) { player.getInventory().setBoots(item); }

    public Player getPlayer() {
        return player;
    }
}
