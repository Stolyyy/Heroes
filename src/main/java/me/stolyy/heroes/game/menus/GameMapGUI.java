package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameEnums;
import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.game.minigame.GameTeam;
import me.stolyy.heroes.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GameMapGUI extends GUI {
    private final List<GameMap> maps;
    private Game game;
    private final PartyModeGUI partyGUI;

    public GameMapGUI(Game game, Player player, PartyModeGUI partyGUI){
        this.game = game;
        this.player = player;
        this.partyGUI = partyGUI;
        this.inventory = Bukkit.createInventory(player, 54, "Map Selection");
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
        GUIListener.isReopening.put(player, true);
        player.openInventory(inventory);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () ->
                GUIListener.isReopening.put(player, false), 1L);
    }

    public void handleClick(int slot){
        if(slot < Math.min(maps.size(),27)) {
            GUIListener.playerGUIMap.put(player, null);
            GameMap map = maps.get(slot);
            Game newGame = new Game(map, GameEnums.GameMode.PARTY);

            newGame.settings().copy(game.settings());

            for (GameEnums.TeamColor color : GameEnums.TeamColor.values()) {
                if(!game.teams().containsKey(color)) continue;
                GameTeam oldTeam = game.teams().get(color);
                GameTeam newTeam = game.teams().get(color);
                newTeam.settings().copy(oldTeam.settings());
                for(Player p : oldTeam.players()){
                    if(color == GameEnums.TeamColor.SPECTATOR) newGame.addSpectator(p);
                    else newGame.addPlayer(p, color);
                }
            }

            game.endGame();
            this.game = newGame;
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
