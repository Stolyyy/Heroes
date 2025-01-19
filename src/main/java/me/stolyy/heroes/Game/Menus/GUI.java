package me.stolyy.heroes.Game.Menus;

import me.stolyy.heroes.Game.Game;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public abstract class GUI {
    protected final Material FILLER_MATERIAL = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    protected final Material PLAYER_FILLER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;

    protected Map<Integer, ItemStack> inventoryItems = new HashMap<>();
    protected Inventory inventory;
    protected Player player;

    public abstract void handleClick(int slot);

    protected ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public abstract void openGUI();
}
