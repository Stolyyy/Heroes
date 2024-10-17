package me.stolyy.heroes.oldGames;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class GameSettingsGUI {
    private final Game game;
    private final Inventory inventory;

    public GameSettingsGUI(Game game) {
        this.game = game;
        this.inventory = Bukkit.createInventory(null, 9, "Game Settings");
        initializeItems();
    }

    private void initializeItems() {
        inventory.setItem(0, createToggleItem(Material.ZOMBIE_HEAD, "Random Heroes", game.getSettings().isRandomHeroes()));
        inventory.setItem(2, createToggleItem(Material.DIAMOND_SWORD, "Friendly Fire", game.getSettings().isFriendlyFire()));
        inventory.setItem(4, createToggleItem(Material.NETHER_STAR, "Smash Crystals", game.getSettings().isSmashCrystals()));
        inventory.setItem(6, createToggleItem(Material.BEACON, "Ultimates", game.getSettings().isUltimates()));
        inventory.setItem(8, createCloseItem());
    }

    private ItemStack createToggleItem(Material material, String name, boolean enabled) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(
                    "",
                    "§7Click to " + (enabled ? "disable" : "enable"),
                    "",
                    enabled ? "§aEnabled" : "§cDisabled"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Close");
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openInventory(Player player) {
        player.openInventory(inventory);
    }

    public void handleClick(Player player, int slot) {
        GameSettingsManager settings = game.getSettings();
        switch (slot) {
            case 0:
                settings.setRandomHeroes(!settings.isRandomHeroes());
                break;
            case 2:
                settings.setFriendlyFire(!settings.isFriendlyFire());
                break;
            case 4:
                settings.setSmashCrystals(!settings.isSmashCrystals());
                break;
            case 6:
                settings.setUltimates(!settings.isUltimates());
                break;
            case 8:
                player.closeInventory();
                game.getGameGUI().openInventory(player);
                return;
        }
        initializeItems();
    }
}