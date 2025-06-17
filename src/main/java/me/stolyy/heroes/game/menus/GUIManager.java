package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.*;

public class GUIManager {
    private static final Map<UUID, GUI> openGuis = new HashMap<>();
    private static final Set<UUID> changingGuis = new HashSet<>();

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
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), gui::open, 1L);
        } else {
            openGuis.remove(playerUuid);
        }
    }

    public static boolean hasOpenGui(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    public static void clear() {
        openGuis.keySet().forEach(uuid -> Objects.requireNonNull(Bukkit.getPlayer(uuid)).closeInventory());
        openGuis.clear();
        changingGuis.clear();
    }
}
