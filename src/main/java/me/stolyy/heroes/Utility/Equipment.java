package me.stolyy.heroes.Utility;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.Pug;
import me.stolyy.heroes.heros.Shoop;
import me.stolyy.heroes.heros.Skullfire;
import me.stolyy.heroes.heros.VoidCrawler;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public class Equipment {
    public static void equip(Player p) {
        Hero h = Heroes.getInstance().getHeroManager().getHero(p);
        ItemStack head = customItem(0, "Dunce Hat");
        ItemStack chest = customArmor("chest", 255, 255, 255, "Dunce Chest");
        ItemStack legs = customArmor("legs", 255, 255, 255, "Dunce Legs");;
        ItemStack boots = customArmor("boots", 255, 255, 255, "Dunce Boots");;
        ItemStack fist = customItem(0, "Pick a character");;
        ItemStack secondary = customItem(5, "Secondary");;
        ItemStack ult = customItem(6, "Ultimate!");;
        if(h instanceof VoidCrawler) {
            head = customItem(16000, "Void Crawler Helmet");
            chest = customArmor("chest", 199, 219, 236, "Void Crawler Chestplate");
            legs = customArmor("legs", 4, 4, 43, "Void Crawler Leggings");
            boots = customArmor("boots", 202, 22, 22, "Void Crawler Boots");
            fist = customItem(16002, "Void Crawler Sword");
        } else if (h instanceof Pug) {
            head = customItem(11000, "Pug face");
            chest = customArmor("chest", 190, 141, 89, "Pug Chest");
            legs = customArmor("legs", 190, 141, 89, "Pug Money Makers");
            boots = customArmor("boots", 190, 141, 89, "Pug Feet");
            fist = customItem(11002, "Pug Hand");
        } else if (h instanceof Shoop) {
            head = customItem(14000, "Im firing");
            chest = customArmor("chest", 0, 158, 16, "Shoop heart");
            legs = customArmor("legs", 32, 28, 29, "Shoop Leg");
            boots = customArmor("boots", 0, 158, 16, "Shoop feet");
            fist = customItem(14002, "Lazor");
        } else if (h instanceof Skullfire) {
            head = customItem(8000, "AARRGGHHGH");
            chest = customArmor("chest", 30, 30, 30, "Skullfire Chest");
            legs = customArmor("legs", 30, 30, 30, "Skullfire Legs");
            boots = customArmor("boots", 30, 30, 30, "Skullfire Feet");
            fist = customItem(8002, "Gun");
        }


        p.getEquipment().setHelmet(head);
        p.getEquipment().setChestplate(chest);
        p.getEquipment().setLeggings(legs);
        p.getEquipment().setBoots(boots);
        p.getInventory().setItem(0, fist);
        p.getInventory().setItem(1, secondary);
        p.getInventory().setItem(2, ult);
    }

                public static ItemStack customItem(int customModelData, String name) {
                    ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
                    ItemMeta meta = item.getItemMeta();

                    if (meta != null) {
                        meta.setDisplayName(name);
                        meta.setCustomModelData(customModelData);
                        meta.setUnbreakable(true);
                        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

                        item.setItemMeta(meta);
                    }

                    return item;
                }

                public static ItemStack customArmor(String type, int r, int g, int b, String name) {
                    Material material;
                    EquipmentSlot slot;

                    switch (type.toLowerCase()) {
                        case "chest":
                            material = Material.LEATHER_CHESTPLATE;
                            slot = EquipmentSlot.CHEST;
                            break;
                        case "legs":
                            material = Material.LEATHER_LEGGINGS;
                            slot = EquipmentSlot.LEGS;
                            break;
                        case "boots":
                            material = Material.LEATHER_BOOTS;
                            slot = EquipmentSlot.FEET;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid armor type. Use 'chest', 'legs', or 'boots'.");
                    }

                    ItemStack armor = new ItemStack(material);
                    LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();

                    if (meta != null) {
                        meta.setColor(Color.fromRGB(r, g, b));
                        meta.setDisplayName(name);
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