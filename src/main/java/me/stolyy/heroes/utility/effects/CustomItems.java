package me.stolyy.heroes.utility.effects;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.stolyy.heroes.hero.data.HeroData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public class CustomItems {

    public static ItemStack customItem(String itemModel, String name) {
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addString(itemModel).addFlag(true).build());
        item.setData(DataComponentTypes.UNBREAKABLE);
        item.setData(DataComponentTypes.MAX_DAMAGE, 100);
        item.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE));

        return item;
    }

    public static ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
        item.setData(DataComponentTypes.UNBREAKABLE);
        item.setData(DataComponentTypes.MAX_DAMAGE, 100);
        item.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE));
        return item;
    }

    public static ItemStack customArmor(String type, int r, int g, int b, String name) {
        Material material = switch (type.toLowerCase()) {
            case "chest" -> Material.LEATHER_CHESTPLATE;
            case "legs" -> Material.LEATHER_LEGGINGS;
            case "boots" -> Material.LEATHER_BOOTS;
            default -> throw new IllegalArgumentException("Invalid armor type. Use 'chest', 'legs', or 'boots'.");
        };

        ItemStack armor = new ItemStack(material);
        armor.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        armor.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
        armor.setData(DataComponentTypes.UNBREAKABLE);
        armor.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(r, g, b)));

        armor.editMeta(meta -> {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        });

        return armor;
    }

    public static ItemStack customArmor(String type, Color color, String name) {
        Material material = switch (type.toLowerCase()) {
            case "chest" -> Material.LEATHER_CHESTPLATE;
            case "legs" -> Material.LEATHER_LEGGINGS;
            case "boots" -> Material.LEATHER_BOOTS;
            default -> throw new IllegalArgumentException("Invalid armor type. Use 'chest', 'legs', or 'boots'.");
        };

        ItemStack armor = new ItemStack(material);
        armor.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        armor.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
        armor.setData(DataComponentTypes.UNBREAKABLE);
        armor.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));

        armor.editMeta(meta -> {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        });

        return armor;
    }

    public static ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.setData(DataComponentTypes.ITEM_NAME, Component.text(player.getName()));
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                .uuid(player.getUniqueId()).build());
        return head;
    }

    public static void setItemModel(ItemStack item, String model) {
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addString(model).addFlag(true).build());
    }

    public static void rename(ItemStack item, String newName){
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(newName));
    }

    public static void addLore(ItemStack item, String... lore) {
        List<TextComponent> loreComponents = Stream.of(lore)
                .map(Component::text)
                .toList();
        item.setData(DataComponentTypes.LORE, ItemLore.lore().
                addLines(loreComponents).build());
    }

    public static void addLore(ItemStack item, TextComponent... lore) {
        List<TextComponent> loreComponents = Stream.of(lore).toList();
        item.setData(DataComponentTypes.LORE, ItemLore.lore().
                addLines(loreComponents).build());
    }

    public static void removeUnbreakable(ItemStack item) {
        item.unsetData(DataComponentTypes.UNBREAKABLE);
        item.editMeta(meta -> {
            meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        });
    }

    public static void enchant(ItemStack item) {
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
    }


    public static void unEnchant(ItemStack item) {
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
    }
}
