package me.stolyy.heroes.Game.Menus;

import me.stolyy.heroes.Game.Game;
import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.Game.GameMap;
import me.stolyy.heroes.Game.GameMapManager;
import me.stolyy.heroes.Game.Party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class PartyMap extends GUI {
    //GUI for changing the map options in Party Mode
    //Changing map will create a new game and reopen gui
    //will also make the old game's gamestate 'ended'
    private final List<GameMap> maps;
    //Slot, Item
    private Game game;
    private PartyGUI partyGUI;

    public PartyMap(Game game, Player player, PartyGUI partyGUI){
        this.game = game;
        this.player = player;
        this.partyGUI = partyGUI;
        this.inventory = Bukkit.createInventory(player, 27, "Map Selection");
        this.maps = new ArrayList<>(GameMapManager.templateMaps);
        initializeMaps();
        for(int slot = 0; slot < 27; slot++) {
            if(!inventoryItems.containsKey(slot)) inventoryItems.put(slot, createItem(FILLER_MATERIAL, " "));
        }
        openGUI();
    }

    public void openGUI(){
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, inventoryItems.get(i));
        }
        GUIListener.playerGUIMap.put(player, this);
        player.openInventory(inventory);
    }

    public void handleClick(int slot){
        if(slot < Math.min(maps.size(),27)) {
            GUIListener.playerGUIMap.put(player, null);
            Map<Player, GameEnums.GameTeam> teams = game.getPlayerTeams();
            GameMap map = maps.get(slot);
            Game newGame = new Game(map, GameEnums.GameMode.PARTY);
            newGame.setPlayerTeams(teams);
            game.gameEnd();
            for(Player p : PartyManager.getPlayersInParty(player)){
                newGame.addPlayer(p);
            }
            partyGUI.setGame(newGame);
            partyGUI.openGUI();
        }
    }

    private void initializeMaps(){
        for(int i = 0; i < Math.min(27,maps.size()); i++){
            GameMap map = maps.get(i);
            inventoryItems.put(i,createMapItem(map));
        }
    }

    private ItemStack createMapItem(GameMap map) {
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
}
