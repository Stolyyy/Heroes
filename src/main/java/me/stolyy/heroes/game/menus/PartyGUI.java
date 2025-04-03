package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameEnums.GameTeam;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class PartyGUI extends GUI{
    //GUI for party mode
    //Move players between teams, with them initially being on spectator
    //Also have options to cancel, start, change maps (new gui), and change game settings (new gui)
    private static final Set<Integer> TEAM_SPOTS = Set.of(27, 29, 33, 35, 36, 38, 42, 44, 45, 47, 51, 53);
    private static final Set<Integer> SPECTATOR_SPOTS = Set.of(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17);
    //Slot, Item
    private Game game;
    private ItemStack lastHead = null;
    //Team slots: 18, 20, 24, 26
    //Maps: 22, Settings: 31, Start: 40, Cancel: 49


    public PartyGUI(Game game, Player player){
        this.game = game;
        this.player = player;
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
        for(int i : SPECTATOR_SPOTS) inventoryItems.put(i, createItem(PLAYER_FILLER_MATERIAL, " "));
        Party party = PartyManager.getPlayerParty(player);
        List<Player> members = new ArrayList<>(party.getMembers());
        members.remove(player);
        inventoryItems.put(27, createPlayerHead(player));
        game.setPlayerTeam(player, GameTeam.RED);
        inventoryItems.put(29, createPlayerHead(members.get(0)));
        game.setPlayerTeam(members.get(0), GameTeam.BLUE);
        for (int i = 1; i < members.size() && i < 18; i++) {
            inventoryItems.put(i-1, createPlayerHead(members.get(i)));
            game.addSpectator(members.get(i));
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
        GUIListener.playerGUIMap.put(player, this);
        GUIListener.isReopening.put(player, true);
        player.openInventory(inventory);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () ->
            GUIListener.isReopening.put(player, false), 1L);
    }

    public void handleClick(int slot){
        switch (slot){
            case 22 -> {
                //map
                GUIListener.playerGUIMap.put(player, null);
                PartyMapGUI mapGUI = new PartyMapGUI(game, player, this);
            } case 31 -> {
                //settings
                //GUIListener.playerGUIMap.put(player, this);
                //PartySettings settingsGUI = new PartySettings(game, player, this);
                player.sendMessage("Settings coming soon!");
            } case 40 -> {
                //start
                if(game.canCountdown()){
                    game.countdown();
                    GUIListener.playerGUIMap.put(player, null);
                    player.closeInventory();
                } else {
                    player.sendMessage(ChatColor.RED + "Cannot Start! Check Team sizes.");
                }
            } case 49 -> {
                //cancel
                game.gameEnd();
                GUIListener.playerGUIMap.put(player, null);
                player.closeInventory();
            } default -> {
                //swap spots
                ItemStack item = inventory.getItem(slot);
                if(lastHead == null){
                    if(item != null && item.getType() == Material.PLAYER_HEAD){
                        lastHead = item.clone();
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                } else if(findSlot(lastHead) == slot){
                    lastHead = null;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                } else if(SPECTATOR_SPOTS.contains(slot) || TEAM_SPOTS.contains(slot)){
                    swapSpots(findSlot(lastHead), slot);
                    lastHead = null;
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
                for (int i = 0; i < 54; i++) inventory.setItem(i, inventoryItems.get(i));
            }
        }
    }

    private void swapSpots(int source, int target){
        ItemStack sourceItem = inventory.getItem(source);
        ItemStack targetItem = inventory.getItem(target);
        inventoryItems.put(source,targetItem);
        inventoryItems.put(target,sourceItem);

        Player sourcePlayer = getPlayer(sourceItem);
        Player targetPlayer = getPlayer(targetItem);

        if (sourcePlayer != null && targetPlayer != null) {
            GameTeam gameTeam1 = game.getPlayerTeams().get(sourcePlayer);
            GameTeam gameTeam2 = game.getPlayerTeams().get(targetPlayer);
            game.setPlayerTeam(sourcePlayer, gameTeam2);
            game.setPlayerTeam(targetPlayer, gameTeam1);
        } else {
            if(target < 18){
                game.setPlayerTeam(sourcePlayer, GameTeam.SPECTATOR);
            } else {
                switch (target % 9){
                    case 0 -> game.setPlayerTeam(sourcePlayer, GameTeam.RED);
                    case 2 -> game.setPlayerTeam(sourcePlayer, GameTeam.BLUE);
                    case 6 -> game.setPlayerTeam(sourcePlayer, GameTeam.GREEN);
                    case 8 -> game.setPlayerTeam(sourcePlayer, GameTeam.YELLOW);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::openGUI, 1L);
    }

    private Player getPlayer(ItemStack head) {
        if (head != null && head.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                return Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
            }
        }
        return null;
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + HeroManager.heroToString(HeroManager.getHero(player)));
        meta.setLore(lore);
        meta.setDisplayName(player.getName());
        head.setItemMeta(meta);
        return head;
    }

    private int findSlot(ItemStack item) {
        if (item == null) return -1;
        for (int i = 0; i < 54; i++) {
            ItemStack it = inventoryItems.get(i);
            if (it != null && it.equals(item)) {
                return i;
            }
        }
        return -1;
    }

    public void setGame(Game game){
        this.game = game;
    }

}
