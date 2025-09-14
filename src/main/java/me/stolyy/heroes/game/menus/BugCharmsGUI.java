package me.stolyy.heroes.game.menus;

import me.stolyy.legacy.heros.HeroManager;
import me.stolyy.legacy.heros.characters.Bug;
import me.stolyy.legacy.heros.characters.Bug.Charms;
import me.stolyy.legacy.Equipment;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;

public class BugCharmsGUI extends GUI {
    private final Set<Charms> charms;
    private Map<Charms, Integer> charmLocations;
    private int usedNotches = 0;

    public BugCharmsGUI(Player player) {
        super(player, 54, "Charm Selection");
        charms = HeroManager.getCharms(player);
        update();
    }

    public void update(){
        HeroManager.setCharms(player, charms);

        for(int i = 0; i < Bug.CHARM_NOTCHES; i++) inventoryItems.put(i, createItem(Material.GRAY_CONCRETE, "Free Charm Notch"));

        for(Charms charm : charmLocations.keySet()) inventoryItems.put(charmLocations.get(charm), Equipment.customItem(charm.texture(), charm.toString(), List.of(Component.text(charm.description()), Component.text(charm.cost()))));

        usedNotches = 0;
        for(Charms charm : charms) {
            usedNotches += charm.cost();
        }

        for(int i = 0; i < usedNotches; i++) inventoryItems.put(i, createItem(Material.LIGHT_GRAY_CONCRETE, "Used Charm Notch"));

        for(Charms charm : charms)
            highlightItem(inventoryItems.get(charmLocations.get(charm)));

        Equipment.equipCharms(player);

        super.update();
    }

    @Override
    protected void populate() {
        charmLocations = new HashMap<>();
        charmLocations.put(Charms.DASHMASTER, 13);
        charmLocations.put(Charms.GRUBSONG, 19);
        charmLocations.put(Charms.HEAVY_BLOW, 20);
        charmLocations.put(Charms.KINGSOUL, 21);
        charmLocations.put(Charms.MARK_OF_PRIDE, 22);
        charmLocations.put(Charms.NAIL_MASTER, 23);
        charmLocations.put(Charms.QUICK_SLASH, 24);
        charmLocations.put(Charms.SHAMAN_STONE, 25);
        charmLocations.put(Charms.SHARP_SHADOW, 28);
        charmLocations.put(Charms.SOUL_EATER, 29);
        charmLocations.put(Charms.SOUL_TWISTER, 30);
        charmLocations.put(Charms.SPRINTMASTER, 31);
        charmLocations.put(Charms.STEADY_BODY, 32);
        charmLocations.put(Charms.STRENGTH, 33);
        charmLocations.put(Charms.WEAVERSONG, 34);
        inventoryItems.put(49,createItem(Material.BARRIER, "Save and Exit"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if(slot == 49) {
            HeroManager.setHero(player, new Bug(player));
            GUIManager.close(player);
            return;
        }
        Charms charm = switch(slot){
            case 13 -> Charms.DASHMASTER;
            case 19 -> Charms.GRUBSONG;
            case 20 -> Charms.HEAVY_BLOW;
            case 21 -> Charms.KINGSOUL;
            case 22 -> Charms.MARK_OF_PRIDE;
            case 23 -> Charms.NAIL_MASTER;
            case 24 -> Charms.QUICK_SLASH;
            case 25 -> Charms.SHAMAN_STONE;
            case 28 -> Charms.SHARP_SHADOW;
            case 29 -> Charms.SOUL_EATER;
            case 30 -> Charms.SOUL_TWISTER;
            case 31 -> Charms.SPRINTMASTER;
            case 32 -> Charms.STEADY_BODY;
            case 33 -> Charms.STRENGTH;
            case 34 -> Charms.WEAVERSONG;
            default -> null;
        };
        if (charm == null) {
            update();
            return;
        }
        if(!charms.remove(charm) && Bug.CHARM_NOTCHES - usedNotches >= charm.cost()){
            charms.add(charm);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);

        update();
    }
}
