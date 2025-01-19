package me.stolyy.heroes.Game.Menus;

import me.stolyy.heroes.Game.Game;
import me.stolyy.heroes.Game.Party.Party;
import me.stolyy.heroes.Game.Party.PartyManager;
import me.stolyy.heroes.HeroManager;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class PartyGUI {
    //GUI for party mode
    //Move players between teams, with them initially being on spectator
    //Also have options to cancel, start, change maps (new gui), and change game settings (new gui)
    private final Material FILLER_MATERIAL = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    private final Material PLAYER_FILLER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final Set<Integer> TEAM_SPOTS = Set.of(18, 20, 24, 26, 27, 29, 33, 35, 36, 38, 44, 46);
    //Slot, Item
    private Map<Integer, ItemStack> inventoryItems = new HashMap<>();
    private Inventory inventory;
    private Game game;
    private Player player;
    private int lastClickedSlot;
    //Team slots: 18, 20, 24, 26
    //Maps: 22, Settings: 31, Start: 40, Cancel: 49


    public PartyGUI(Game game, Player player){
        this.game = game;
        this.inventory = Bukkit.createInventory(player, 54, "Party Game Setup");
        inventoryItems.put(22,createItem(Material.MAP, "Change Map"));
        inventoryItems.put(31,createItem(Material.BONE_MEAL, "Game Settings"));
        inventoryItems.put(40,createItem(Material.DIAMOND, "Start Game!"));
        inventoryItems.put(49,createItem(Material.BARRIER, "Cancel"));
        inventoryItems.put(18,createItem(Material.RED_CONCRETE, "Red Team:"));
        inventoryItems.put(20,createItem(Material.BLUE_CONCRETE, "Blue Team:"));
        inventoryItems.put(24,createItem(Material.GREEN_CONCRETE, "Green Team:"));
        inventoryItems.put(26,createItem(Material.YELLOW_CONCRETE, "Yellow Team:"));
        for(int i : TEAM_SPOTS) inventoryItems.put(i, createItem(PLAYER_FILLER_MATERIAL, " "));
        inventoryItems.put(27,createPlayerHead(player));

        Party party = PartyManager.getPlayerParty(player);
        assert party != null;
        int i = -1;
        for(Player p : party.getMembers()){
            if (i < 0) {
                inventoryItems.put(29, createPlayerHead(p));
            } else {
                inventoryItems.put(i, createPlayerHead(p));
            }
            i++;
        }

        for(int slot = 0; slot < 54; slot++) {
            if(!inventoryItems.containsKey(slot)) inventoryItems.put(slot, createItem(FILLER_MATERIAL, " "));
        }
        openGUI();
    }

    public void openGUI(){
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, inventoryItems.get(i));
        }
        player.openInventory(inventory);
    }

    public void handleClick(int slot){

    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        HeroManager heroManager = Heroes.getInstance().getHeroManager();
        meta.setOwningPlayer(player);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + heroManager.heroToString(player));
        meta.setLore(lore);
        meta.setDisplayName(player.getName());
        head.setItemMeta(meta);
        return head;
    }
}
