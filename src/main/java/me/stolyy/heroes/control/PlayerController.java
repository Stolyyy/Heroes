package me.stolyy.heroes.control;

import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.UUID;

public class PlayerController implements Controller {
    private final Player player;

    public PlayerController(Player player) {
        this.player = player;
    }

    @Override
    public LivingEntity getEntity() {
        // A stale Player object survives logout, so a null-check on getEntity() could never
        // detect a departed player. Returning null when offline makes "reference lost"
        // detectable (Game.gameTick backstop). Still non-null during PlayerQuitEvent handling.
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
    public org.bukkit.util.BoundingBox getBoundingBox() { return player.getBoundingBox(); }

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
                java.time.Duration.ofMillis(fadeInTicks * 50L),
                java.time.Duration.ofMillis(stayTicks * 50L),
                java.time.Duration.ofMillis(fadeOutTicks * 50L));
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
    public void playSound(Object sound, float volume, float pitch) {
        if (sound instanceof Sound s) {
            SoundBuilder.of(s).volume(volume).pitch(pitch).play(player);
        } else if (sound instanceof String s) {
            SoundBuilder.of(s).volume(volume).pitch(pitch).play(player);
        }
    }

    @Override
    public void updateEnergyDisplay(float percent, int value) {
        player.setLevel(value);
        player.setExp(Math.max(0, Math.min(1, percent)));
    }

    @Override
    public void updateItemCooldown(int slotIndex, float progress) {
        // Assuming Hotbar slots 0, 1, 2
        ItemStack item = player.getInventory().getItem(slotIndex);
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int max = item.getType().getMaxDurability();
            int damage = (int) (max * progress);
            damageable.setDamage(damage);
            item.setItemMeta(meta);
        }
    }
    
    @Override public void setInventoryItem(int slot, ItemStack item) { player.getInventory().setItem(slot, item); }
    @Override public void setHelmet(ItemStack item) { player.getInventory().setHelmet(item); }
    @Override public void setChestplate(ItemStack item) { player.getInventory().setChestplate(item); }
    @Override public void setLeggings(ItemStack item) { player.getInventory().setLeggings(item); }
    @Override public void setBoots(ItemStack item) { player.getInventory().setBoots(item); }

    public Player getPlayer() {
        return player;
    }
}
