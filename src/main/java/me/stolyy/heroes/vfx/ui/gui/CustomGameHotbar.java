package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hotbar shown to a custom game's host after they close {@link CustomGameGUI} with Escape:
 * reopen the GUI, start the game, or cancel it. Removed again when the GUI reopens or the game
 * starts/cancels. Clicks are handled by {@link CustomGameHotbarListener}.
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

    private CustomGameHotbar() {}

    public static void give(Player player, CustomGameGUI gui) {
        guis.put(player.getUniqueId(), gui);
        for (Action action : Action.values()) {
            ItemStack item = ItemBuilder.from(action.material)
                    .name(GameConfigManager.getMessage("custom-gui.hotbar." + action.key + ".name"))
                    .lore(GameConfigManager.getMessageList("custom-gui.hotbar." + action.key + ".lore", Map.of()).stream()
                            .map(line -> line.decoration(TextDecoration.ITALIC, false))
                            .toArray(Component[]::new))
                    .build();
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action.name());
            item.setItemMeta(meta);
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
    }

    /** The GUI this player's hotbar belongs to, or null if none is active. */
    static CustomGameGUI guiOf(Player player) {
        return guis.get(player.getUniqueId());
    }

    /** Forgets the player's hotbar without touching the inventory (quit cleanup). */
    static void forget(UUID uuid) {
        guis.remove(uuid);
    }

    /** The action a tagged hotbar item performs, or null if the item isn't one of ours. */
    public static Action actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        return raw == null ? null : Action.valueOf(raw);
    }
}
