package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ShardKind;
import me.stolyy.heroes.progression.SkinShards;
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
import java.util.Locale;
import java.util.Map;

/**
 * Paginated hero picker for redeeming a {@link ShardKind#HERO_SELECT} shard of a given rarity.
 * Only lists heroes that have at least one unowned eligible skin in {@link SkinShards#eligiblePool}.
 */
public class ShardHeroSelectGUI extends GUI {
    private static final int PAGE_CAP = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    private final Rarity rarity;
    private final @Nullable GUI returnTo;
    private final Pager pager = new Pager(PAGE_CAP);
    private final Map<Integer, String> slotToHero = new HashMap<>();
    private int totalHeroes;

    public ShardHeroSelectGUI(Player player, Rarity rarity, @Nullable GUI returnTo) {
        super(player, 54, GuiText.raw("gui.shards.hero-select-title", "rarity", SkinShards.formatRarity(rarity)));
        this.rarity = rarity;
        this.returnTo = returnTo;
    }

    @Override
    protected void populate() {
        inventoryItems.clear();
        slotToHero.clear();

        Map<String, List<SkinShards.PoolEntry>> byHero = SkinShards.eligiblePoolByHero(player, rarity);
        List<Map.Entry<String, List<SkinShards.PoolEntry>>> entries = new ArrayList<>(byHero.entrySet());
        totalHeroes = entries.size();
        String rarityName = SkinShards.formatRarity(rarity);

        List<Map.Entry<String, List<SkinShards.PoolEntry>>> slice = pager.slice(entries);
        for (int i = 0; i < slice.size(); i++) {
            Map.Entry<String, List<SkinShards.PoolEntry>> entry = slice.get(i);
            String heroId = entry.getKey();
            int count = entry.getValue().size();

            List<Component> lore = List.of(
                    GuiText.component("gui.shards.hero-select-count-lore", "count", count),
                    Component.empty(),
                    GuiText.component("gui.shards.hero-select-click-lore", "rarity", rarityName)
            );
            ItemStack icon = ItemBuilder.from(Material.SPONGE)
                    .name(GuiText.component("gui.shards.hero-select-item-name", "hero", HeroConfig.formatName(heroId)))
                    .lore(lore)
                    .modelData("gui", "heroes", heroId.toLowerCase(Locale.ROOT))
                    .build();
            inventoryItems.put(i, icon);
            slotToHero.put(i, heroId);
        }

        if (pager.hasPrev()) {
            inventoryItems.put(SLOT_PREV, ItemBuilder.from(Material.ARROW)
                    .name(GuiText.component("gui.nav.prev-page"))
                    .build());
        }
        inventoryItems.put(SLOT_BACK, backItem());
        if (pager.hasNext(totalHeroes)) {
            inventoryItems.put(SLOT_NEXT, ItemBuilder.from(Material.ARROW)
                    .name(GuiText.component("gui.nav.next-page"))
                    .build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_BACK) {
            returnToParent();
            return;
        }
        if (slot == SLOT_PREV && pager.prev()) {
            playClick();
            populate();
            update();
            return;
        }
        if (slot == SLOT_NEXT && pager.next(totalHeroes)) {
            playClick();
            populate();
            update();
            return;
        }

        String heroId = slotToHero.get(slot);
        if (heroId == null) return;

        SkinShards.ConsumeResult result = SkinShards.consumeHeroSelect(player, rarity, heroId);
        String rarityName = SkinShards.formatRarity(rarity);
        switch (result.failure()) {
            case NO_SHARDS -> {
                playError();
                player.sendMessage(GameConfigManager.getMessage("shards.none-kind", Map.of(
                        "rarity", rarityName,
                        "kind", ShardKind.HERO_SELECT.displayName())));
                returnToParent();
            }
            case EMPTY_POOL -> {
                playError();
                player.sendMessage(GameConfigManager.getMessage("shards.empty-pool", Map.of("rarity", rarityName)));
                returnToParent();
            }
            case NONE -> {
                ShardsGUI.announceWon(player, result.won(), rarity);
                PlayerStats stats = PlayerStatsManager.getStats(player);
                if (stats.getShardCount(ShardKind.HERO_SELECT, rarity) <= 0
                        || SkinShards.eligiblePool(player, rarity).isEmpty()) {
                    returnToParent();
                } else {
                    populate();
                    update();
                }
            }
        }
    }

    private void returnToParent() {
        if (returnTo != null) GUIManager.open(player, returnTo);
        else GUIManager.close(player);
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
