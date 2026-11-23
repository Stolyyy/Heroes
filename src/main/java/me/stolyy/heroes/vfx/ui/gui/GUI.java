package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.vfx.effect.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;

public abstract class GUI implements InventoryHolder {

    protected final int size;
    protected Map<Integer, ItemStack> inventoryItems;
    protected Inventory inventory;
    protected Player player;

    public GUI(Player player, int size, String title) {
        this.player = player;
        this.size = size;
        this.inventoryItems = new HashMap<>(size);
        this.inventory = Bukkit.createInventory(this, size, Component.text(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        populate();
        update();
        player.openInventory(inventory);
    }

    public abstract void handleClick(InventoryClickEvent event);

    protected abstract void populate();

    protected void update() {
        inventory.clear();
        for (Map.Entry<Integer, ItemStack> entry : inventoryItems.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
    }

    public void cleanup() { }

    public boolean isLocked() {
        return false;
    }

    protected GUI getParentGUI() {
        return null;
    }

    // Helper Methods

    protected ItemStack createItem(Material material, String name) {
        return ItemBuilder.from(material)
                .name("<white>" + name)
                .build();
    }

    /** Shared background filler (B20.4) — textured via the gui_general_filler model key. */
    protected ItemStack fillerItem() {
        return ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .modelData("gui", "general", "filler")
                .build();
    }

    /** Fills every slot without content with the background filler. Call before super.update(). */
    protected void fillEmptySlots() {
        ItemStack filler = fillerItem();
        for (int i = 0; i < size; i++) {
            inventoryItems.putIfAbsent(i, filler);
        }
    }

    protected ItemStack createPlayerHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            head.setItemMeta(meta);
        }
        return head;
    }

    protected void highlightItem(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
    }

    protected void removeHighlight(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(false);
            item.setItemMeta(meta);
        }
    }
}
