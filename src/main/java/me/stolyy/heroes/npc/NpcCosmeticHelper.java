package me.stolyy.heroes.npc;

import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Helper class for converting HeroSkin configuration to ItemStacks for NPC equipment.
 */
public class NpcCosmeticHelper {

    /**
     * Builds the helmet ItemStack from a HeroSkin.
     * If no head override exists, builds the default model: heroName_head_skinId.
     */
    public static ItemStack buildHelmet(HeroSkin skin, String heroName) {
        if (skin == null) return null;

        ConfigurationSection head = skin.getHeadOverride();
        if (head != null) {
            return buildItemFromConfig(head);
        }

        return ItemBuilder.from(Material.SPONGE).unbreakable(true)
                .modelData(heroName, "head", skin.getId()).build();
    }

    /**
     * Builds the chestplate ItemStack from a HeroSkin.
     */
    public static ItemStack buildChestplate(HeroSkin skin) {
        if (skin == null) return null;

        ConfigurationSection armor = skin.getArmor();
        if (armor == null) return null;

        // Check for subsection first (future-proofing)
        ConfigurationSection chest = armor.getConfigurationSection("chestplate");
        if (chest != null) {
            return buildItemFromConfig(chest);
        }

        // Fall back to simple RGB string format: "R,G,B"
        String colorStr = armor.getString("chest");
        return buildColoredLeatherArmorFromRgb(Material.LEATHER_CHESTPLATE, colorStr);
    }

    /**
     * Builds the leggings ItemStack from a HeroSkin.
     */
    public static ItemStack buildLeggings(HeroSkin skin) {
        if (skin == null) return null;

        ConfigurationSection armor = skin.getArmor();
        if (armor == null) return null;

        // Check for subsection first
        ConfigurationSection legs = armor.getConfigurationSection("leggings");
        if (legs != null) {
            return buildItemFromConfig(legs);
        }

        // Fall back to simple RGB string format
        String colorStr = armor.getString("legs");
        return buildColoredLeatherArmorFromRgb(Material.LEATHER_LEGGINGS, colorStr);
    }

    /**
     * Builds the boots ItemStack from a HeroSkin.
     */
    public static ItemStack buildBoots(HeroSkin skin) {
        if (skin == null) return null;

        ConfigurationSection armor = skin.getArmor();
        if (armor == null) return null;

        // Check for subsection first
        ConfigurationSection boots = armor.getConfigurationSection("boots");
        if (boots != null) {
            return buildItemFromConfig(boots);
        }

        // Fall back to simple RGB string format
        String colorStr = armor.getString("boots");
        return buildColoredLeatherArmorFromRgb(Material.LEATHER_BOOTS, colorStr);
    }

    /**
     * Builds the main hand weapon ItemStack from a HeroSkin.
     * If no hand override exists, builds the default model: heroName_hand_skinId.
     */
    public static ItemStack buildWeapon(HeroSkin skin, String heroName) {
        if (skin == null) return null;

        ConfigurationSection hand = skin.getHandOverride();
        if (hand != null) {
            return buildItemFromConfig(hand);
        }

        return ItemBuilder.from(Material.STICK).unbreakable(true)
                .modelData(heroName, "hand", skin.getId()).build();
    }

    /**
     * Builds an ItemStack from a configuration section.
     */
    private static ItemStack buildItemFromConfig(ConfigurationSection config) {
        if (config == null) return null;

        String materialName = config.getString("material", "AIR");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (material == Material.AIR) return null;

        ItemStack item = new ItemStack(material);

        // Handle custom model data
        if (config.contains("custom_model_data")) {
            item.editMeta(meta -> meta.setCustomModelData(config.getInt("custom_model_data")));
        }

        // Handle leather armor color
        if (isLeatherArmor(material) && config.contains("color")) {
            item.editMeta(LeatherArmorMeta.class, meta -> {
                Color color = parseColor(config.getString("color"));
                if (color != null) {
                    meta.setColor(color);
                }
            });
        }

        return item;
    }

    /**
     * Builds colored leather armor from an RGB string like "R,G,B".
     */
    private static ItemStack buildColoredLeatherArmorFromRgb(Material material, String colorStr) {
        if (!isLeatherArmor(material)) return null;
        if (colorStr == null || colorStr.isEmpty()) return new ItemStack(material);

        ItemStack item = new ItemStack(material);
        Color color = parseColor(colorStr);
        if (color != null) {
            item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
        }

        return item;
    }

    /**
     * Parses a color string (RGB comma-separated, hex, or named color).
     */
    private static Color parseColor(String colorStr) {
        if (colorStr == null) return null;

        colorStr = colorStr.trim();

        // Handle RGB comma-separated format: "R,G,B"
        if (colorStr.contains(",")) {
            String[] parts = colorStr.split(",");
            if (parts.length == 3) {
                try {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return Color.fromRGB(r, g, b);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        // Handle hex colors
        if (colorStr.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(colorStr.substring(1), 16);
                return Color.fromRGB(rgb);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Handle named colors
        return switch (colorStr.toLowerCase()) {
            case "red" -> Color.RED;
            case "blue" -> Color.BLUE;
            case "green" -> Color.GREEN;
            case "yellow" -> Color.YELLOW;
            case "orange" -> Color.ORANGE;
            case "purple" -> Color.PURPLE;
            case "black" -> Color.BLACK;
            case "white" -> Color.WHITE;
            case "gray", "grey" -> Color.GRAY;
            case "aqua", "cyan" -> Color.AQUA;
            case "lime" -> Color.LIME;
            case "maroon" -> Color.MAROON;
            case "navy" -> Color.NAVY;
            case "olive" -> Color.OLIVE;
            case "silver" -> Color.SILVER;
            case "teal" -> Color.TEAL;
            case "fuchsia" -> Color.FUCHSIA;
            default -> null;
        };
    }

    /**
     * Checks if a material is leather armor.
     */
    private static boolean isLeatherArmor(Material material) {
        return material == Material.LEATHER_HELMET ||
               material == Material.LEATHER_CHESTPLATE ||
               material == Material.LEATHER_LEGGINGS ||
               material == Material.LEATHER_BOOTS;
    }
}
