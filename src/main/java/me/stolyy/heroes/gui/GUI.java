package me.stolyy.heroes.game.menus;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GUI {
    protected ItemStack FILLER_ITEM = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");

    protected final int size;
    protected Map<Integer, ItemStack> inventoryItems;
    protected Inventory inventory;
    protected Player player;

    public GUI(Player player, int size, String title) {
        this.player = player;
        this.size = size;
        this.inventoryItems = new HashMap<>(size);
        this.inventory = Bukkit.createInventory(player, size, Component.text(title));
        populate();
    }

    public void open(){
        update();
        player.openInventory(inventory);
    }

    public abstract void handleClick(InventoryClickEvent event);

    protected abstract void populate();

    protected void update(){
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, inventoryItems.getOrDefault(i, FILLER_ITEM));
        }
    }

    public boolean isLocked(){
        return false;
    }
}
