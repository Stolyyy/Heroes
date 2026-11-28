package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.SkinShards;
import me.stolyy.heroes.progression.SkinShards.CombineRecipe;
import me.stolyy.heroes.progression.SkinShards.CombineSpec;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin.Rarity;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shard combine workbench: one row per rarity (Normal / Rare / Legendary). Legendary intentionally
 * has no rarity-upgrade button.
 */
public class ShardCombineGUI extends GUI {
    private record SlotAction(CombineRecipe recipe, Rarity rarity) {}

    private static final int SLOT_BACK = 35;

    private final @Nullable GUI returnTo;
    private final Map<Integer, SlotAction> actions = new HashMap<>();

    public ShardCombineGUI(Player player, @Nullable GUI returnTo) {
        super(player, 36, GuiText.raw("gui.shards.combine-title"));
        this.returnTo = returnTo;
    }

    @Override
    protected void populate() {
        inventoryItems.clear();
        actions.clear();
        PlayerStats stats = PlayerStatsManager.getStats(player);

        // Row 0: Normal
        putCombine(1, stats, CombineRecipe.UPGRADE_RARITY, Rarity.NORMAL, Material.PRISMARINE_SHARD);
        putCombine(4, stats, CombineRecipe.TO_HERO_SELECT, Rarity.NORMAL, Material.IRON_INGOT);
        putCombine(7, stats, CombineRecipe.TO_SKIN_SELECT, Rarity.NORMAL, Material.PAPER);

        // Row 1: Rare
        putCombine(10, stats, CombineRecipe.UPGRADE_RARITY, Rarity.RARE, Material.AMETHYST_SHARD);
        putCombine(13, stats, CombineRecipe.TO_HERO_SELECT, Rarity.RARE, Material.DIAMOND);
        putCombine(16, stats, CombineRecipe.TO_SKIN_SELECT, Rarity.RARE, Material.ENCHANTED_BOOK);

        // Row 2: Legendary (no rarity upgrade offered)
        putCombine(22, stats, CombineRecipe.TO_HERO_SELECT, Rarity.LEGENDARY, Material.NETHER_STAR);
        putCombine(25, stats, CombineRecipe.TO_SKIN_SELECT, Rarity.LEGENDARY, Material.TOTEM_OF_UNDYING);

        inventoryItems.put(SLOT_BACK, backItem());
    }

    private void putCombine(int slot, PlayerStats stats, CombineRecipe recipe, Rarity rarity, Material enabledMat) {
        CombineSpec spec = SkinShards.resolveCombine(recipe, rarity);
        if (spec == null) return;

        int have = stats.getShardCount(spec.fromKind(), spec.fromRarity());
        boolean canAfford = have >= SkinShards.COMBINE_COST;

        String fromLabel = describe(spec.fromRarity(), spec.fromKind().displayName());
        String toLabel = describe(spec.toRarity(), spec.toKind().displayName());

        Component title = switch (recipe) {
            case UPGRADE_RARITY -> GuiText.component("gui.shards.combine-upgrade-name",
                    "from_rarity", SkinShards.formatRarity(spec.fromRarity()),
                    "to_rarity", SkinShards.formatRarity(spec.toRarity()));
            case TO_HERO_SELECT -> GuiText.component("gui.shards.combine-hero-name",
                    "rarity", SkinShards.formatRarity(rarity));
            case TO_SKIN_SELECT -> GuiText.component("gui.shards.combine-skin-name",
                    "rarity", SkinShards.formatRarity(rarity));
        };

        List<Component> lore = new ArrayList<>();
        lore.add(GuiText.component("gui.shards.combine-cost-lore", "input", fromLabel));
        lore.add(GuiText.component("gui.shards.combine-result-lore", "output", toLabel));
        lore.add(GuiText.component("gui.shards.combine-have-lore", "have", have));
        lore.add(Component.empty());
        lore.add(GuiText.component(canAfford
                ? "gui.shards.combine-click-lore"
                : "gui.shards.combine-need-more-lore"));

        ItemStack item = ItemBuilder.from(canAfford ? enabledMat : Material.GRAY_DYE)
                .name(title)
                .lore(lore)
                .build();
        inventoryItems.put(slot, item);
        actions.put(slot, new SlotAction(recipe, rarity));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_BACK) {
            if (returnTo != null) GUIManager.open(player, returnTo);
            else GUIManager.close(player);
            return;
        }

        SlotAction action = actions.get(slot);
        if (action == null) return;

        CombineSpec spec = SkinShards.resolveCombine(action.recipe(), action.rarity());
        if (spec == null) return;
        String fromLabel = describe(spec.fromRarity(), spec.fromKind().displayName());
        String toLabel = describe(spec.toRarity(), spec.toKind().displayName());

        if (!SkinShards.combine(player, action.recipe(), action.rarity())) {
            playError();
            player.sendMessage(GameConfigManager.getMessage("shards.combine-insufficient",
                    Map.of("input", fromLabel)));
            populate();
            update();
            return;
        }

        playConfirm();
        player.sendMessage(GameConfigManager.getMessage("shards.combined", Map.of(
                "input", fromLabel,
                "output", toLabel)));
        populate();
        update();
    }

    private static String describe(Rarity rarity, String kindDisplay) {
        return SkinShards.formatRarity(rarity) + " " + kindDisplay;
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
