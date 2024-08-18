package me.stolyy.heroes.Games;

import me.stolyy.heroes.Games.Game;
import me.stolyy.heroes.Games.GameEnums;
import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GameGUI {
    private final Heroes plugin;
    private final Game game;
    private Inventory inventory;
    private final int[] teamSlots = {18, 20, 24, 26}; // Slots for team concrete blocks
    private final Material FILLER_MATERIAL = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    private final Material PLAYER_FILLER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;

    public GameGUI(Heroes plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
        this.inventory = Bukkit.createInventory(null, 54, "Party Game Setup");
        initializeInventory();
    }

    private void initializeInventory() {
        // Fill the entire inventory with light gray stained glass panes
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, createItem(FILLER_MATERIAL, " "));
        }

        // Add team indicators
        inventory.setItem(teamSlots[0], createItem(Material.RED_CONCRETE, "Red Team"));
        inventory.setItem(teamSlots[1], createItem(Material.BLUE_CONCRETE, "Blue Team"));
        inventory.setItem(teamSlots[2], createItem(Material.GREEN_CONCRETE, "Green Team"));
        inventory.setItem(teamSlots[3], createItem(Material.YELLOW_CONCRETE, "Yellow Team"));

        // Add control items in the middle column
        inventory.setItem(22, createItem(Material.MAP, "Map Options"));
        inventory.setItem(31, createItem(Material.COMPARATOR, "Game Settings"));
        inventory.setItem(40, createItem(Material.DIAMOND, "Start Game"));
        inventory.setItem(49, createItem(Material.BARRIER, "Cancel"));

        updateInventory();
    }

    public void updateInventory() {
        // Clear player slots in the top two rows
        for (int i = 0; i < 18; i++) {
            inventory.setItem(i, createItem(PLAYER_FILLER_MATERIAL, " "));
        }

        // Clear team areas
        for (int teamSlot : teamSlots) {
            for (int i = 0; i < 3; i++) {
                int slot = teamSlot + 9 * (i + 1);
                inventory.setItem(slot, createItem(FILLER_MATERIAL, " "));
            }
        }

        // Add player heads
        Map<Player, GameEnums.GameTeam> players = game.getPlayers();
        int topRowIndex = 0;

        for (Map.Entry<Player, GameEnums.GameTeam> entry : players.entrySet()) {
            Player player = entry.getKey();
            GameEnums.GameTeam team = entry.getValue();

            if (team == null) {
                // Unassigned players go in the top two rows
                if (topRowIndex < 18) {
                    inventory.setItem(topRowIndex, createPlayerHead(player));
                    topRowIndex++;
                }
            } else {
                // Assigned players go in their team's column
                int teamIndex = team.ordinal();
                int slot = getNextAvailableSlot(teamSlots[teamIndex]);
                if (slot != -1) {
                    inventory.setItem(slot, createPlayerHead(player));
                }
            }
        }
    }

    private int getNextAvailableSlot(int startSlot) {
        for (int i = 1; i <= 3; i++) {
            int slot = startSlot + 9 * i;
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == FILLER_MATERIAL) {
                return slot;
            }
        }
        return -1; // No available slot
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(player.getName());
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public void openInventory(Player player) {
        updateInventory(); // Ensure the inventory is up-to-date before opening
        player.openInventory(inventory);
    }


    public void handleInventoryClick(Player player, int slot) {
        if (slot == 40) { // Start Game button
            if (canStartGame()) {
                game.startGame();
                player.closeInventory();
            } else {
                player.sendMessage(Component.text("Cannot start the game. Ensure there are players on at least two teams.").color(NamedTextColor.RED));
            }
        } else if (slot == 22) { // Map Options
            openMapOptionsMenu(player);
        } else if (slot == 31) { // Game Settings
            openGameSettingsMenu(player);
        } else if (slot == 49) {
            player.closeInventory();
            game.removePlayer(player);
            plugin.teleportToSpawn(player);
            game.unrestrictAllPlayers();
        }
    }

    public void handlePlayerMove(Player player, ItemStack headItem, int toSlot) {
        if (headItem != null && headItem.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) headItem.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                Player movedPlayer = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
                if (movedPlayer != null) {
                    GameEnums.GameTeam newTeam = getTeamForSlot(toSlot);
                    GameEnums.GameTeam oldTeam = game.getPlayerTeam(movedPlayer);
                    if (newTeam != oldTeam) {
                        game.setPlayerTeam(movedPlayer, newTeam);
                        if (newTeam != null) {
                            game.teleportPlayerToSpawnPoint(movedPlayer);
                        } else {
                            game.teleportToWaitingArea(movedPlayer);
                        }
                        updateInventory();
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

                        // Reopen the GUI for the player who moved the head
                        Bukkit.getScheduler().runTaskLater(plugin, () -> openInventory(player), 1L);
                    }
                }
            }
        }
    }

    private boolean canStartGame() {
        Set<GameEnums.GameTeam> teamsWithPlayers = new HashSet<>();
        for (GameEnums.GameTeam team : game.getPlayers().values()) {
            if (team != null) {
                teamsWithPlayers.add(team);
            }
        }
        return teamsWithPlayers.size() >= 2;
    }

    private void openMapOptionsMenu(Player player) {
        // Implement map options menu
        player.sendMessage(Component.text("Map options menu not yet implemented.").color(NamedTextColor.YELLOW));
    }

    private void openGameSettingsMenu(Player player) {
        // Implement game settings menu
        player.sendMessage(Component.text("Game settings menu not yet implemented.").color(NamedTextColor.YELLOW));
    }

    private GameEnums.GameTeam getTeamForSlot(int slot) {
        if (slot < 18) return null; // Unassigned players area
        for (int i = 0; i < teamSlots.length; i++) {
            int columnStart = teamSlots[i] % 9;
            if (slot % 9 == columnStart && slot >= teamSlots[i] && slot < 54) {
                return GameEnums.GameTeam.values()[i];
            }
        }
        return null;
    }


}