package me.stolyy.heroes.oldGames;

import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.HeroManager;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GameGUI {
    private final Game game;
    private Inventory inventory;
    private final int[] teamSlots = {18, 20, 24, 26}; // Slots for team concrete blocks
    private final Material FILLER_MATERIAL = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    private final Material PLAYER_FILLER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private ItemStack selectedHead = null;

    public GameGUI(Game game) {
        this.game = game;
        this.inventory = Bukkit.createInventory(null, 54, "PartyC Game Setup");
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
        inventory.setItem(22, createItem(Material.MAP, "Select GameMap"));
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
            for (int i = 1; i <= 3; i++) {
                int slot = teamSlot + 9 * i;
                inventory.setItem(slot, createItem(FILLER_MATERIAL, " "));
            }
        }

        // Add player heads
        Map<UUID, GameEnums.GameTeam> players = game.getPlayers();
        int topRowIndex = 0;

        for (Map.Entry<UUID, GameEnums.GameTeam> entry : players.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            GameEnums.GameTeam team = entry.getValue();

            if (player != null) {
                ItemStack head = createPlayerHead(player);
                if (selectedHead != null && isSamePlayer(selectedHead, head)) {
                    ItemMeta meta = head.getItemMeta();
                    meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
                    head.setItemMeta(meta);
                }

                if (team == null) {
                    // Unassigned players go in the top two rows
                    if (topRowIndex < 18) {
                        inventory.setItem(topRowIndex, head);
                        topRowIndex++;
                    }
                } else {
                    // Assigned players go in their team's column
                    int teamIndex = team.ordinal();
                    int slot = getNextAvailableSlot(teamSlots[teamIndex]);
                    if (slot != -1) {
                        inventory.setItem(slot, head);
                    }
                }
            }
        }

        // Update map item
        ItemStack mapItem = inventory.getItem(22);
        if (mapItem != null) {
            ItemMeta meta = mapItem.getItemMeta();
            if (meta != null) {
                MapData currentMap = game.getCurrentMap();
                String mapName = currentMap != null ? currentMap.getName() : "No map selected";
                meta.setDisplayName("Select GameMap: " + mapName);
                mapItem.setItemMeta(meta);
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
        HeroManager heroManager = Heroes.getInstance().getHeroManager();
        if (meta != null) {
            meta.setOwningPlayer(player);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + heroManager.heroToString(player));
            meta.setLore(lore);
            meta.setDisplayName(player.getName());
            head.setItemMeta(meta);
        }
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

    public void handleClick(Player player, int slot) {
        if (slot == 22) { // GameMap selection
            new MapGUI(game).openInventory(player);
        } else if (slot == 31) { // Game settings
            new GameSettingsGUI(game).openInventory(player);
        } else if (slot == 40) { // Start game
            if (game.canStart()) {
                game.startGame();
                player.closeInventory();
            } else {
                player.sendMessage("Cannot start the game. Ensure there are players on at least two teams.");
            }
        } else if (slot == 49) { // Cancel
            player.closeInventory();
            game.getPlugin().getGameManager().cancelGame(game.getGameId(), player);
        } else {
            handlePlayerMove(player, inventory.getItem(slot), slot);
        }
    }

    private void handlePlayerMove(Player player, ItemStack clickedItem, int toSlot) {
        if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
            if (selectedHead == null) {
                // First click - select the head
                selectedHead = clickedItem.clone();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                updateInventory();
            } else if (isSamePlayer(selectedHead, clickedItem)) {
                // Clicked the same player again - deselect
                selectedHead = null;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                updateInventory();
            } else {
                // Clicked a different player - swap them
                swapPlayers(selectedHead, clickedItem);
                selectedHead = null;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                updateInventory();
            }
        } else if (selectedHead != null) {
            // Clicked on a non-head slot with a head selected - move the player
            movePlayer(selectedHead, toSlot);
            selectedHead = null;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            updateInventory();
        }
    }

    private void swapPlayers(ItemStack head1, ItemStack head2) {
        Player player1 = getPlayerFromHead(head1);
        Player player2 = getPlayerFromHead(head2);

        if (player1 != null && player2 != null) {
            GameEnums.GameTeam team1 = game.getPlayerTeam(player1);
            GameEnums.GameTeam team2 = game.getPlayerTeam(player2);

            game.setPlayerTeam(player1, team2);
            game.setPlayerTeam(player2, team1);
        }
    }

    private void movePlayer(ItemStack head, int toSlot) {
        Player player = getPlayerFromHead(head);
        if (player != null) {
            GameEnums.GameTeam newTeam = getTeamForSlot(toSlot);
            game.setPlayerTeam(player, newTeam);
        }
    }

    private Player getPlayerFromHead(ItemStack head) {
        if (head != null && head.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                return Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
            }
        }
        return null;
    }

    private GameEnums.GameTeam getTeamForSlot(int slot) {
        if (slot < 18) return null; // Spectator area
        for (int i = 0; i < teamSlots.length; i++) {
            int columnStart = teamSlots[i] % 9;
            if (slot % 9 == columnStart && slot >= teamSlots[i] && slot < 54) {
                return GameEnums.GameTeam.values()[i];
            }
        }
        return null;
    }

    private boolean isSamePlayer(ItemStack head1, ItemStack head2) {
        if (head1.getType() != Material.PLAYER_HEAD || head2.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        SkullMeta meta1 = (SkullMeta) head1.getItemMeta();
        SkullMeta meta2 = (SkullMeta) head2.getItemMeta();
        return meta1 != null && meta2 != null &&
                meta1.getOwningPlayer() != null && meta2.getOwningPlayer() != null &&
                meta1.getOwningPlayer().getUniqueId().equals(meta2.getOwningPlayer().getUniqueId());
    }
}