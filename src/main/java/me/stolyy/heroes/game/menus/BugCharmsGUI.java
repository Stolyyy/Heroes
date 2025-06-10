package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.heros.characters.Bug;
import me.stolyy.heroes.heros.characters.Bug.Charms;
import me.stolyy.heroes.utility.effects.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class BugCharmsGUI extends GUI {
    private final Set<Charms> charms;
    private final Map<Charms, Integer> charmLocations = new HashMap<>();
    private int usedNotches = 0;

    public BugCharmsGUI(Player player) {
        this.inventory = Bukkit.createInventory(player, 54, "Charm Selection");
        this.player = player;
        charms = HeroManager.getCharms(player);

        charmLocations.put(Charms.DASHMASTER, 13);
        charmLocations.put(Charms.GRUBSONG, 18);
        charmLocations.put(Charms.HEAVY_BLOW, 19);
        charmLocations.put(Charms.KINGSOUL, 20);
        charmLocations.put(Charms.MARK_OF_PRIDE, 21);
        charmLocations.put(Charms.NAIL_MASTER, 22);
        charmLocations.put(Charms.QUICK_SLASH, 23);
        charmLocations.put(Charms.SHAMAN_STONE, 24);
        charmLocations.put(Charms.SHARP_SHADOW, 27);
        charmLocations.put(Charms.SOUL_EATER, 28);
        charmLocations.put(Charms.SPRINTMASTER, 29);
        charmLocations.put(Charms.STEADY_BODY, 30);
        charmLocations.put(Charms.STRENGTH, 31);
        charmLocations.put(Charms.WEAVERSONG, 32);
        inventoryItems.put(49,createItem(Material.BARRIER, "Cancel"));

        update();

        openGUI();
    }

    public void update(){
        HeroManager.setCharms(player, charms);

        for(int i = 0; i < 8; i++) inventoryItems.put(i, createItem(Material.GRAY_CONCRETE, "Free Charm Notch"));

        for(Charms charm : charmLocations.keySet()) inventory.setItem(charmLocations.get(charm), Equipment.customItem(charm.texture(), charm.toString(), List.of(charm.description(), String.valueOf(charm.cost()))));

        usedNotches = 0;
        for(Charms charm : charms) {
            usedNotches += charm.cost();
            enchantItem(charmLocations.get(charm));
        }

        for(int i = 0; i < usedNotches; i++) inventoryItems.put(i, createItem(Material.LIGHT_GRAY_CONCRETE, "Used Charm Notch"));

        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, inventoryItems.get(i));
        }
    }

    @Override
    public void handleClick(int slot) {
        if(slot == 49) {
            GUIListener.playerGUIMap.put(player, null);
            player.closeInventory();
            return;
        }
        Charms charm = switch(slot){
            case 13 -> Charms.DASHMASTER;
            case 18 -> Charms.GRUBSONG;
            case 19 -> Charms.HEAVY_BLOW;
            case 20 -> Charms.KINGSOUL;
            case 21 -> Charms.MARK_OF_PRIDE;
            case 22 -> Charms.NAIL_MASTER;
            case 23 -> Charms.QUICK_SLASH;
            case 24 -> Charms.SHAMAN_STONE;
            case 27 -> Charms.SHARP_SHADOW;
            case 28 -> Charms.SOUL_EATER;
            case 29 -> Charms.SPRINTMASTER;
            case 30 -> Charms.STEADY_BODY;
            case 31 -> Charms.STRENGTH;
            case 32 -> Charms.WEAVERSONG;
            default -> null;
        };
        if (charm == null) {
            return;
        }
        if(!charms.remove(charm) && Bug.CHARM_NOTCHES - usedNotches >= charm.cost()){
            charms.add(charm);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);

        update();
    }

    @Override
    public void openGUI() {
        player.openInventory(inventory);
    }
}
