package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GUIManager {
    private static final Map<UUID, GUI> openGuis = new HashMap<>();
    private static final Set<UUID> changingGuis = new HashSet<>();

    public static void initialize() {
        Heroes plugin = Heroes.getInstance();
        Bukkit.getPluginManager().registerEvents(new GUIListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new CustomGameHotbarListener(), plugin);
    }

    public static void open(Player player, GUI gui) {
        changingGuis.add(player.getUniqueId());
        gui.open();
        openGuis.put(player.getUniqueId(), gui);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> changingGuis.remove(player.getUniqueId()), 1L);
    }

    public static void close(Player player) {
        changingGuis.add(player.getUniqueId());
        openGuis.remove(player.getUniqueId());
        player.closeInventory();


        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> changingGuis.remove(player.getUniqueId()), 1L);
    }

    public static void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        GUI gui = openGuis.get(player.getUniqueId());
        if (gui != null) {
            event.setCancelled(true);
            // A cancelled pickup can still trigger the client's collect-to-cursor gesture on a
            // quick second press, delivering a DOUBLE_CLICK event for the same physical click.
            // Paper's isLeftClick() treats DOUBLE_CLICK as a left click, so cycle-style GUIs would
            // otherwise interpret it as a second activation — the preceding LEFT event already
            // performed the action, so this supplementary event must not be forwarded.
            if (event.getClick() == ClickType.DOUBLE_CLICK) return;
            gui.handleClick(event);
        }
    }

    public static void handleClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        if (changingGuis.contains(playerUuid)) {
            return;
        }

        GUI gui = openGuis.get(playerUuid);
        if (gui == null) return;

        if (gui.isLocked()) {
            // Re-check ownership on the reopen tick. Between now and then the GUI's owner may have
            // closed it deliberately (GUIManager.close clears openGuis) or replaced it with another
            // GUI — reopening regardless resurrects a dead GUI on top of whatever is there now, and
            // it keeps firing its stale click callbacks.
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
                if (openGuis.get(playerUuid) == gui) open(player, gui);
            }, 1L);
        } else {
            GUI parent = gui.getParentGUI();
            if (parent != null) {
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> open(player, parent), 1L);
            } else {
                GUI removed = openGuis.remove(playerUuid);
                if (removed != null) {
                    removed.cleanup();
                }
            }
        }
    }

    public static boolean hasOpenGui(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    /** The GUI the player currently has open, or null. Lets callers branch on the type
     *  (e.g. the respawn countdown pauses while a HeroSelectGUI re-pick is open). */
    public static GUI getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    public static void clear() {
        List<UUID> open = new ArrayList<>(openGuis.keySet());
        openGuis.clear();
        changingGuis.clear();
        for (UUID uuid : open) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
    }

    public static void closeAndCleanup(Player player) {
        GUI gui = openGuis.remove(player.getUniqueId());
        if (gui != null) {
            gui.cleanup();
        }
        changingGuis.remove(player.getUniqueId());
    }
}
