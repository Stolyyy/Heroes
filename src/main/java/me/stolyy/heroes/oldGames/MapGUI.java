package me.stolyy.heroes.oldGames;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MapGUI {
    private final Game game;
    private final Inventory inventory;
    private final List<MapData> availableMaps;

    public MapGUI(Game game) {
        this.game = game;
        this.availableMaps = Heroes.getInstance().getGameManager().getAllMaps();
        this.inventory = Bukkit.createInventory(null, 54, "Select GameMap");
        initializeInventory();
    }

    private void initializeInventory() {
        for (int i = 0; i < availableMaps.size() && i < 45; i++) {
            MapData map = availableMaps.get(i);
            inventory.setItem(i, createMapItem(map));
        }
        inventory.setItem(49, createItem(Material.BARRIER, "Cancel"));
    }

    private ItemStack createMapItem(MapData map) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(map.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to select this map");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openInventory(Player player) {
        player.openInventory(inventory);
    }

    public void handleClick(Player player, int slot) {
        if (slot < 45 && slot < availableMaps.size()) {
            MapData selectedMap = availableMaps.get(slot);
            game.setMap(selectedMap);
            player.sendMessage("GameMap set to: " + selectedMap.getName());
            player.closeInventory();
            // Reopen the main game setup GUI
            game.getGameGUI().openInventory(player);
        } else if (slot == 49) { // Cancel
            player.closeInventory();
            // Reopen the main game setup GUI
            game.getGameGUI().openInventory(player);
        }
    }
}
