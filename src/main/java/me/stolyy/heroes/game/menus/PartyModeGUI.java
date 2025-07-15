package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameEnums.TeamColor;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.game.minigame.GameTeam;
import me.stolyy.heroes.heros.HeroManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

//cancel, start, change maps (new gui), change game settings (new gui)
public class PartyModeGUI extends GUI{
    protected final ItemStack PLAYER_FILLER_ITEM;

    private Map<TeamColor, List<Integer>> teamSpots;
    private Game game;
    private int selectedSlot = -1;


    public PartyModeGUI(Game game, Player player){
        super(player, 54, "Party Game Setup");
        PLAYER_FILLER_ITEM = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        FILLER_ITEM = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        this.game = game;
        update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        switch(slot){
            case 31 -> {
                if(game.canStart()) {
                    game.start();
                    GUIManager.close(player);
                } else {
                    player.sendMessage(Component.text("Cannot Start! Check Team sizes.", NamedTextColor.RED));
                }
            }
            case 40 -> GUIManager.open(player, new GameSettingsGUI(game, player, this));
            case 49 -> {
                GameManager.leaveGame(player, true);
                GUIManager.close(player);
            }
            case 18, 20, 24, 26 -> {
                TeamColor color = teamFromSlot(slot + 9);
                GUIManager.open(player, new TeamSettingsGUI(game.getTeams().get(color), player, this));
            }
            default -> handlePlayerSlotClick(slot);
        }
    }

    private void handlePlayerSlotClick(int clickedSlot) {
        if (!isTeamSpot(clickedSlot)) return;

        ItemStack clickedItem = inventory.getItem(clickedSlot);

        //Select player
        if (selectedSlot == -1) {
            if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
                selectedSlot = clickedSlot;
                highlightItem(clickedItem);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
        }
        else if (clickedItem != null){
            //Deselect
            if (selectedSlot == clickedSlot) {
                removeHighlight(clickedItem);
                selectedSlot = -1;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            }
            //Swap
            else {
                ItemStack sourceItem = inventory.getItem(selectedSlot);
                if(sourceItem != null) removeHighlight(sourceItem);
                swapSpots(selectedSlot, clickedSlot);
                selectedSlot = -1;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }

    @Override
    protected void populate(){
        teamSpots = new HashMap<>();
        inventoryItems.put(40,createItem(Material.NETHER_STAR, "Game Settings"));
        inventoryItems.put(31,createItem(Material.DIAMOND, "Start Game!"));
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
    }

    @Override
    protected void update(){
        for(List<Integer> list : teamSpots.values())
            for(int i : list) inventoryItems.put(i, PLAYER_FILLER_ITEM);

        for(GameTeam team : game.getTeams().values()){
            int i = 0;
            for(Player p : team.onlinePlayers()){
                inventoryItems.put(teamSpots.get(team.color()).get(i), createPlayerHead(p));
                i++;
            }
        }

        super.update();
    }

    private void swapSpots(int source, int target){
        ItemStack sourceItem = inventory.getItem(source);
        ItemStack targetItem = inventory.getItem(target);

        Player sourcePlayer = getPlayer(sourceItem);
        Player targetPlayer = getPlayer(targetItem);

        if (targetPlayer != null) {
            TeamColor sourceTeam = game.playerColor(sourcePlayer);
            TeamColor targetTeam = game.playerColor(targetPlayer);
            game.changeTeam(sourcePlayer, targetTeam);
            game.changeTeam(targetPlayer, sourceTeam);
        } else {
            game.changeTeam(sourcePlayer, teamFromSlot(target));
        }
        update();
    }

    private boolean isTeamSpot(int slot){
        for(List<Integer> list : teamSpots.values())
            for(int i : list) if(i == slot) return true;
        return false;
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

    public void setGame(Game newGame) {
        this.game = newGame;
    }

    @Override
    public boolean isLocked() {
        return true;
    }
}
