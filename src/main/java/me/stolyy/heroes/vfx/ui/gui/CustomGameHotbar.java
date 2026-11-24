package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hotbar items shown to a custom game's host after closing {@link CustomGameGUI} with Escape:
 * reopen the GUI, start the game, or cancel it. Saves and restores displaced slot contents.
 */
public final class CustomGameHotbar {

    public enum Action {
        REOPEN(3, Material.COMPASS, "reopen"),
        START(4, Material.EMERALD, "start"),
        CANCEL(5, Material.BARRIER, "cancel");

        final int slot;
        final Material material;
        final String key;

        Action(int slot, Material material, String key) {
            this.slot = slot;
            this.material = material;
            this.key = key;
        }
    }

    private static final NamespacedKey ACTION_KEY = new NamespacedKey(Heroes.getInstance(), "custom_game_hotbar");
    private static final Map<UUID, CustomGameGUI> guis = new HashMap<>();
    private static final Map<UUID, Map<Integer, ItemStack>> savedItems = new HashMap<>();

    private CustomGameHotbar() {}

    public static void give(Player player, CustomGameGUI gui) {
        guis.put(player.getUniqueId(), gui);
        Map<Integer, ItemStack> backup = savedItems.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        for (Action action : Action.values()) {
            ItemStack existing = player.getInventory().getItem(action.slot);
            if (actionOf(existing) == null && !backup.containsKey(action.slot)) {
                backup.put(action.slot, existing != null ? existing.clone() : null);
            }
            ItemStack item = ItemBuilder.from(action.material)
                    .name(GameConfigManager.getMessage("custom-gui.hotbar." + action.key + ".name"))
                    .lore(GameConfigManager.getMessageList("custom-gui.hotbar." + action.key + ".lore", Map.of()))
                    .build();
            item.editMeta(meta -> meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action.name()));
            player.getInventory().setItem(action.slot, item);
        }
    }

    public static void remove(Player player) {
        guis.remove(player.getUniqueId());
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (actionOf(player.getInventory().getItem(i)) != null) {
                player.getInventory().setItem(i, null);
            }
        }
        Map<Integer, ItemStack> backup = savedItems.remove(player.getUniqueId());
        if (backup != null) {
            backup.forEach((slot, item) -> {
                if (player.getInventory().getItem(slot) == null && item != null) {
                    player.getInventory().setItem(slot, item);
                }
            });
        }
    }

    static CustomGameGUI guiOf(Player player) {
        return guis.get(player.getUniqueId());
    }

    public static Action actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        return raw == null ? null : Action.valueOf(raw);
    }
}
