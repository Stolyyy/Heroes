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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paginated skin picker for redeeming a {@link ShardKind#SKIN_SELECT} shard of a given rarity.
 * Lists every unowned eligible skin from {@link SkinShards#eligiblePool} and grants the chosen one.
 */
public class ShardSkinSelectGUI extends GUI {
    private static final int PAGE_CAP = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    private final Rarity rarity;
    private final @Nullable GUI returnTo;
    private final Pager pager = new Pager(PAGE_CAP);
    private final Map<Integer, SkinShards.PoolEntry> slotToEntry = new HashMap<>();
    private int totalSkins;

    public ShardSkinSelectGUI(Player player, Rarity rarity, @Nullable GUI returnTo) {
        super(player, 54, GuiText.raw("gui.shards.skin-select-title", "rarity", SkinShards.formatRarity(rarity)));
        this.rarity = rarity;
        this.returnTo = returnTo;
    }

    @Override
    protected void populate() {
        inventoryItems.clear();
        slotToEntry.clear();

        List<SkinShards.PoolEntry> pool = SkinShards.eligiblePool(player, rarity);
        totalSkins = pool.size();
        String rarityKey = rarity.name().toLowerCase(Locale.ROOT);

        List<SkinShards.PoolEntry> slice = pager.slice(pool);
        for (int i = 0; i < slice.size(); i++) {
            SkinShards.PoolEntry entry = slice.get(i);
            String heroId = entry.heroId();

            List<Component> lore = List.of(
                    GuiText.component("gui.shards.skin-select-hero-lore", "hero", HeroConfig.formatName(heroId)),
                    GuiText.component("gui.skins.rarity." + rarityKey),
                    Component.empty(),
                    GuiText.component("gui.shards.skin-select-click-lore")
            );
            ItemStack icon = ItemBuilder.from(Material.SPONGE)
                    .name(GuiText.component("gui.shards.skin-select-item-name", "skin", entry.skin().getDisplayName()))
                    .lore(lore)
                    .modelData(HeroConfig.assetKey(heroId), "head", entry.skin().getId())
                    .build();
            inventoryItems.put(i, icon);
            slotToEntry.put(i, entry);
        }

        if (pager.hasPrev()) {
            inventoryItems.put(SLOT_PREV, ItemBuilder.from(Material.ARROW)
                    .name(GuiText.component("gui.nav.prev-page"))
                    .build());
        }
        inventoryItems.put(SLOT_BACK, backItem());
        if (pager.hasNext(totalSkins)) {
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
        if (slot == SLOT_NEXT && pager.next(totalSkins)) {
            playClick();
            populate();
            update();
            return;
        }

        SkinShards.PoolEntry entry = slotToEntry.get(slot);
        if (entry == null) return;

        SkinShards.ConsumeResult result = SkinShards.consumeSkinSelect(
                player, rarity, entry.heroId(), entry.skin().getId());
        String rarityName = SkinShards.formatRarity(rarity);
        switch (result.failure()) {
            case NO_SHARDS -> {
                playError();
                player.sendMessage(GameConfigManager.getMessage("shards.none-kind", Map.of(
                        "rarity", rarityName,
                        "kind", ShardKind.SKIN_SELECT.displayName())));
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
                if (stats.getShardCount(ShardKind.SKIN_SELECT, rarity) <= 0
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
