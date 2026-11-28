package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ShardKind;
import me.stolyy.heroes.progression.SkinShards;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin.Rarity;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skin Shard inventory and redemption menu. Top row opens {@link ShardKind#RANDOM} shards per
 * rarity; bottom row reserves inert placeholders for part 2 combine / selection shard actions.
 */
public class ShardsGUI extends GUI {
    private static final int SLOT_NORMAL = 11;
    private static final int SLOT_RARE = 13;
    private static final int SLOT_LEGENDARY = 15;
    private static final int SLOT_COMBINE_PLACEHOLDER = 21;
    private static final int SLOT_SELECT_PLACEHOLDER = 23;
    private static final int SLOT_BACK = 26;

    private final @Nullable GUI returnTo;

    public ShardsGUI(Player player, @Nullable GUI returnTo) {
        super(player, 27, GuiText.raw("gui.shards.title"));
        this.returnTo = returnTo;
    }

    @Override
    protected void populate() {
        PlayerStats stats = PlayerStatsManager.getStats(player);

        inventoryItems.put(SLOT_NORMAL, randomShardItem(stats, Rarity.NORMAL, Material.QUARTZ));
        inventoryItems.put(SLOT_RARE, randomShardItem(stats, Rarity.RARE, Material.PRISMARINE_SHARD));
        inventoryItems.put(SLOT_LEGENDARY, randomShardItem(stats, Rarity.LEGENDARY, Material.AMETHYST_SHARD));

        // Inert placeholders reserving space for Part 2 (#90) combine and selection shard actions.
        inventoryItems.put(SLOT_COMBINE_PLACEHOLDER, ItemBuilder.from(Material.GRAY_DYE)
                .name(GuiText.component("gui.shards.combine-placeholder-name"))
                .lore(GuiText.lore("gui.shards.coming-soon-lore"))
                .build());
        inventoryItems.put(SLOT_SELECT_PLACEHOLDER, ItemBuilder.from(Material.GRAY_DYE)
                .name(GuiText.component("gui.shards.select-placeholder-name"))
                .lore(GuiText.lore("gui.shards.coming-soon-lore"))
                .build());

        inventoryItems.put(SLOT_BACK, backItem());
    }

    private org.bukkit.inventory.ItemStack randomShardItem(PlayerStats stats, Rarity rarity, Material material) {
        String key = rarity.name().toLowerCase(Locale.ROOT);
        int count = stats.getShardCount(ShardKind.RANDOM, rarity);
        int poolSize = SkinShards.eligiblePool(player, rarity).size();

        List<Component> lore = new ArrayList<>();
        lore.add(GuiText.component("gui.shards.owned-lore", "count", count));
        lore.add(GuiText.component("gui.shards.pool-lore", "pool", poolSize));
        lore.add(Component.empty());
        if (count <= 0) {
            lore.add(GuiText.component("gui.shards.none-lore"));
        } else if (poolSize <= 0) {
            lore.add(GuiText.component("gui.shards.empty-pool-lore"));
        } else {
            lore.add(GuiText.component("gui.shards.consume-lore"));
        }

        return ItemBuilder.from(material)
                .amount(Math.max(1, Math.min(64, count)))
                .name(GuiText.component("gui.shards.rarity-" + key + "-name"))
                .lore(lore)
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_NORMAL -> consume(Rarity.NORMAL);
            case SLOT_RARE -> consume(Rarity.RARE);
            case SLOT_LEGENDARY -> consume(Rarity.LEGENDARY);
            case SLOT_BACK -> {
                if (returnTo != null) GUIManager.open(player, returnTo);
                else GUIManager.close(player);
            }
        }
    }

    private void consume(Rarity rarity) {
        SkinShards.ConsumeResult result = SkinShards.consumeRandom(player, rarity);
        String rarityName = SkinShards.formatRarity(rarity);
        switch (result.failure()) {
            case NO_SHARDS -> {
                playError();
                player.sendMessage(GameConfigManager.getMessage("shards.none", Map.of("rarity", rarityName)));
            }
            case EMPTY_POOL -> {
                playError();
                player.sendMessage(GameConfigManager.getMessage("shards.empty-pool", Map.of("rarity", rarityName)));
            }
            case NONE -> {
                SkinShards.PoolEntry won = result.won();
                SoundBuilder.of(Sound.UI_TOAST_CHALLENGE_COMPLETE).pitch(1.1f).play(player);
                player.sendMessage(GameConfigManager.getMessage("shards.consumed", Map.of(
                        "skin", won.skin().getDisplayName(),
                        "hero", HeroConfig.formatName(won.heroId()),
                        "rarity", rarityName)));
                populate();
                update();
            }
        }
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
