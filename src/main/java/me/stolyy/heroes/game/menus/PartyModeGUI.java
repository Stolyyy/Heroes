package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameEnums.TeamColor;
import me.stolyy.heroes.game.minigame.GameTeam;
import me.stolyy.heroes.heros.HeroManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

//cancel, start, change maps (new gui), change game settings (new gui)
public class PartyModeGUI extends GUI{
    private final Map<TeamColor, List<Integer>> teamSpots = new HashMap<>();
    private Game game;
    private ItemStack lastHead = null;


    public PartyModeGUI(Game game, Player player){
        this.game = game;
        this.player = player;
        this.inventory = Bukkit.createInventory(player, 54, "Party Game Setup");
        inventoryItems.put(31,createItem(Material.BONE_MEAL, "Game Settings"));
        inventoryItems.put(40,createItem(Material.DIAMOND, "Start Game!"));
        inventoryItems.put(49,createItem(Material.BARRIER, "Cancel"));
        inventoryItems.put(18,createItem(Material.RED_CONCRETE, "Red Team:"));
        inventoryItems.put(20,createItem(Material.BLUE_CONCRETE, "Blue Team:"));
        inventoryItems.put(24,createItem(Material.GREEN_CONCRETE, "Green Team:"));
        inventoryItems.put(26,createItem(Material.YELLOW_CONCRETE, "Yellow Team:"));

        teamSpots.put(TeamColor.RED, List.of(27, 36, 45));
        teamSpots.put(TeamColor.BLUE, List.of(29, 38, 47));
        teamSpots.put(TeamColor.GREEN, List.of(33, 42, 51));
        teamSpots.put(TeamColor.YELLOW, List.of(35, 44, 53));
        teamSpots.put(TeamColor.SPECTATOR, List.of(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17));

        update();

        for(int slot = 0; slot < 54; slot++) {
            if(!inventoryItems.containsKey(slot)) inventoryItems.put(slot, createItem(FILLER_MATERIAL, " "));
        }

        openGUI();
    }

    private void update(){
        for(List<Integer> list : teamSpots.values())
            for(int i : list) inventoryItems.put(i, createItem(PLAYER_FILLER_MATERIAL, " "));

        for(GameTeam team : game.teams().values()){
            int i = 0;
            for(Player p : team.players()){
                inventoryItems.put(teamSpots.get(team.color()).get(i), createPlayerHead(p));
                i++;
            }
        }

        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, inventoryItems.get(i));
        }
    }

    public void openGUI(){
        update();
        GUIListener.playerGUIMap.put(player, this);
        GUIListener.isReopening.put(player, true);
        player.openInventory(inventory);
        GUIListener.isReopening.put(player, false);
    }

    public void handleClick(int slot){
        switch (slot){
            case 31 -> {
                GUIListener.playerGUIMap.put(player, null);
                new GameSettingsGUI(game, player, this);
            }
            case 40 -> { // start
                if(game.canCountdown()){
                    game.countdown();
                    GUIListener.playerGUIMap.put(player, null);
                    player.closeInventory();
                } else {
                    player.sendMessage(Component.text("Cannot Start! Check Team sizes.", NamedTextColor.RED));
                }
            } case 49 -> { // cancel
                game.clean();
                GUIListener.playerGUIMap.put(player, null);
                player.closeInventory();
            } case 18, 20, 24, 26 -> { // team settings
                TeamColor color = teamFromSlot(slot + 9);
                GUIListener.playerGUIMap.put(player, null);
                new TeamSettingsGUI(game.teams().get(color), player, this);
            } default -> { // select or swap
                ItemStack item = inventory.getItem(slot);
                //select
                if(lastHead == null){
                    if(item != null && item.getType() == Material.PLAYER_HEAD){
                        lastHead = item.clone();
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
                //de-select
                else if(findSlot(lastHead) == slot){
                    lastHead = null;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                }
                //swap
                else if(isTeamSpot(slot)){
                    swapSpots(findSlot(lastHead), slot);
                    lastHead = null;
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
                update();
            }
        }
    }

    private boolean isTeamSpot(int slot){
        for(List<Integer> list : teamSpots.values())
            for(int i : list) if(i == slot) return true;
        return false;
    }



    private void swapSpots(int source, int target){
        ItemStack sourceItem = inventory.getItem(source);
        ItemStack targetItem = inventory.getItem(target);

        Player sourcePlayer = getPlayer(sourceItem);
        Player targetPlayer = getPlayer(targetItem);

        if (sourcePlayer != null && targetPlayer != null) {
            TeamColor sourceTeam = game.playerColor(sourcePlayer);
            TeamColor targetTeam = game.playerColor(targetPlayer);
            game.changeTeam(sourcePlayer, targetTeam);
            game.changeTeam(targetPlayer, sourceTeam);
        } else {
            game.changeTeam(sourcePlayer, teamFromSlot(target));
        }
        update();
    }

    private TeamColor teamFromSlot(int slot){
        for(var entry : teamSpots.entrySet())
            for(int i : entry.getValue()) if(i == slot) return entry.getKey();
        return null;
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
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(HeroManager.heroToString(HeroManager.getHero(player)), NamedTextColor.WHITE));
        meta.lore(lore);
        meta.displayName(Component.text(player.getName()));
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
