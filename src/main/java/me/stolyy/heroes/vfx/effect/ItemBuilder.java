package me.stolyy.heroes.vfx.effect;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    // We store component changes in a list of consumers to apply them AFTER ItemMeta is set
    private final List<Consumer<ItemStack>> componentModifiers = new ArrayList<>();

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder from(Material material) { return new ItemBuilder(material); }
    public static ItemBuilder from(ItemStack item) { return new ItemBuilder(item); }

    public ItemBuilder name(Component name) {
        if (meta != null) {
            meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return this;
    }

    public ItemBuilder name(String miniMessage) {
        return name(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder lore(Component... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<Component> lines) {
        if (meta != null) {
            List<Component> stripped = new ArrayList<>(lines.size());
            for (Component line : lines) {
                stripped.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            meta.lore(stripped);
        }
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        if (unbreakable) {
            componentModifiers.add(i -> {
                i.setData(DataComponentTypes.UNBREAKABLE);
                i.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                        .addHiddenComponents(DataComponentTypes.UNBREAKABLE)
                        .build());
            });
        } else {
            componentModifiers.add(i -> i.unsetData(DataComponentTypes.UNBREAKABLE));
        }
        return this;
    }

    public ItemBuilder maxDamage(int maxDamage) {
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(maxDamage);
        }
        return this;
    }

    public ItemBuilder enchantGlint(boolean enchantGlint){
        componentModifiers.add(i -> i.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, enchantGlint));
        return this;
    }

    public ItemBuilder modelData(ModelRef ref, String liveSkin) {
        return modelData(ref.hero(), ref.texture(), ref.resolveSkin(liveSkin));
    }

    public ItemBuilder modelData(String hero, String texture, String skin) {
        String key = (hero + "_" + texture + "_" + skin).toLowerCase();
        componentModifiers.add(i -> {
            CustomModelData modelData = CustomModelData.customModelData()
                    .addString(key)
                    .build();
            i.setData(DataComponentTypes.CUSTOM_MODEL_DATA, modelData);
        });
        return this;
    }

    public ItemBuilder color(Color color) {
        if (meta instanceof LeatherArmorMeta leather) leather.setColor(color);
        else if (meta instanceof PotionMeta potion) potion.setColor(color);
        return this;
    }

    /**
     * Overrides the worn-armor equipment asset on this item's {@code equippable} component,
     * preserving the base material's slot, equip sound, and other equippable properties.
     */
    public ItemBuilder equippable(Key assetId, EquipmentSlot fallbackSlot) {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(fallbackSlot, "fallbackSlot must not be null");
        componentModifiers.add(i -> {
            Equippable base = i.getData(DataComponentTypes.EQUIPPABLE);
            Equippable.Builder builder = base != null ? base.toBuilder() : Equippable.equippable(fallbackSlot);
            i.setData(DataComponentTypes.EQUIPPABLE, builder.assetId(assetId).build());
        });
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }

        for (Consumer<ItemStack> modifier : componentModifiers) {
            modifier.accept(item);
        }

        return item;
    }

    public static void applyModelData(ItemStack item, String hero, String texture, String skin) {
        applyModelData(item, hero + "_" + texture + "_" + skin);
    }

    /** Raw-key variant for keys that don't follow the hero_texture_skin convention (map icons). */
    public static void applyModelData(ItemStack item, String rawKey) {
        CustomModelData modelData = CustomModelData.customModelData()
                .addString(rawKey.toLowerCase())
                .build();
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, modelData);
    }

    public static void applyDamage(ItemStack item, int damage) {
        item.setData(DataComponentTypes.DAMAGE, damage);
    }

    public static void applyAmount(ItemStack item, int amount) {
        item.setAmount(Math.max(1, amount));
    }
}
