package me.stolyy.legacy;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import me.stolyy.legacy.heros.Hero;
import me.stolyy.legacy.heros.HeroManager;
import me.stolyy.legacy.heros.characters.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Equipment {
    //Give player proper equipment (armor, tools) based on hero
    public static void equip(Player p) {
        Hero h = HeroManager.getHero(p);
        ItemStack head = customItem(0, "Hat");
        ItemStack chest = customArmor("chest", 255, 255, 255, "Chest");
        ItemStack legs = customArmor("legs", 255, 255, 255, "Legs");
        ItemStack boots = customArmor("boots", 255, 255, 255, "Boots");
        ItemStack fist = customItem(0, "Pick a character");
        ItemStack secondary = breakableItem(5, "Secondary");
        ItemStack ult = breakableItem(6, "Ultimate!");
        if(h instanceof VoidCrawler) {
            head = customItem(16000, "Void Crawler Helmet");
            chest = customArmor("chest", 199, 219, 236, "Void Crawler Chestplate");
            legs = customArmor("legs", 4, 4, 43, "Void Crawler Leggings");
            boots = customArmor("boots", 202, 22, 22, "Void Crawler Boots");
            fist = customItem(16002, "Void Crawler Sword");
        } else if (h instanceof Pug) {
            head = customItem(11000, "Pug face");
            chest = customArmor("chest", 190, 141, 89, "Pug Chest");
            legs = customArmor("legs", 190, 141, 89, "Pug Legs");
            boots = customArmor("boots", 190, 141, 89, "Pug Feet");
            fist = customItem(11002, "Pug Hand");
        } else if (h instanceof Shoop) {
            head = customItem(14000, "Im firing");
            secondary = customItem(4, "Secondary");
            chest = customArmor("chest", 0, 158, 16, "Shoop heart");
            legs = customArmor("legs", 32, 28, 29, "Shoop Leg");
            boots = customArmor("boots", 0, 158, 16, "Shoop feet");
            fist = customItem(14002, "Lazor");
        } else if (h instanceof Skullfire) {
            head = customItem(8000, "help i am on fire");
            chest = customArmor("chest", 30, 30, 30, "Skullfire Chest");
            legs = customArmor("legs", 30, 30, 30, "Skullfire Legs");
            boots = customArmor("boots", 30, 30, 30, "Skullfire Boots");
            fist = customItem(8002, "Gun");
        } else if (h instanceof Bug){
            head = customItem(17000, "shell");
            chest = customArmor("chest", 30, 30, 50, "Bug Chest");
            legs = customArmor("legs", 30, 30, 50, "Bug Legs");
            boots = customArmor("boots", 30, 30, 50, "Bug Boots");
            fist = customItem(17002, "Nail");
            equipCharms(p);
        }


        p.getEquipment().setHelmet(head);
        p.getEquipment().setChestplate(chest);
        p.getEquipment().setLeggings(legs);
        p.getEquipment().setBoots(boots);
        p.getInventory().setItem(0, fist);
        p.getInventory().setItem(1, secondary);
        p.getInventory().setItem(2, ult);
    }

    public static void equipCharms(Player player){
        //CLEARS TOP ROW OF PLAYER INVENTORY
        Set<Bug.Charms> charms = HeroManager.getCharms(player);
        Iterator<Bug.Charms> charmsIterator = charms.iterator();
        for(int i = 9; i < 18; i++){
            if(charmsIterator.hasNext()) {
                Bug.Charms charm = charmsIterator.next();
                player.getInventory().setItem(i, Equipment.customItem(charm.texture(), charm.toString(), List.of(Component.text(charm.description()), Component.text(charm.cost()))));
            } else {
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            }
        }
    }

    //Create carrot on a stick with custom model data
    public static ItemStack customItem(int customModelData, String name) {
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.setCustomModelData(customModelData);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            item.setItemMeta(meta);
        }

        return item;
    }

    public static ItemStack customItem(int customModelData, String name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.setCustomModelData(customModelData);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.lore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    public static ItemStack breakableItem(int customModelData, String name) {
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.setCustomModelData(customModelData);

            item.setItemMeta(meta);
        }

        return item;
    }

    //Create armor with RGB coloring
    public static ItemStack customArmor(String type, int r, int g, int b, String name) {
        Material material = switch (type.toLowerCase()) {
            case "chest" -> Material.LEATHER_CHESTPLATE;
            case "legs" -> Material.LEATHER_LEGGINGS;
            case "boots" -> Material.LEATHER_BOOTS;
            default ->
                    throw new IllegalArgumentException("Invalid armor type. Use 'chest', 'legs', or 'boots'.");
        };

        ItemStack armor = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();

        if (meta != null) {
            meta.setColor(Color.fromRGB(r, g, b));
            meta.displayName(Component.text(name));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

            // Remove all attribute modifiers
            Multimap<Attribute, AttributeModifier> emptyModifiers = LinkedHashMultimap.create();
            meta.setAttributeModifiers(emptyModifiers);

            armor.setItemMeta(meta);
        }

        return armor;
    }
}
