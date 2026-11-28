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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skin Shard hub: row 1 redeems {@link ShardKind#RANDOM} shards directly, row 2 opens hero pickers
 * for {@link ShardKind#HERO_SELECT}, row 3 opens skin pickers for {@link ShardKind#SKIN_SELECT},
 * and the bottom bar opens {@link ShardCombineGUI}.
 */
public class ShardsGUI extends GUI {
    private static final int SLOT_RANDOM_NORMAL = 2;
    private static final int SLOT_RANDOM_RARE = 4;
    private static final int SLOT_RANDOM_LEGENDARY = 6;

    private static final int SLOT_HERO_NORMAL = 11;
    private static final int SLOT_HERO_RARE = 13;
    private static final int SLOT_HERO_LEGENDARY = 15;

    private static final int SLOT_SKIN_NORMAL = 20;
    private static final int SLOT_SKIN_RARE = 22;
    private static final int SLOT_SKIN_LEGENDARY = 24;

    private static final int SLOT_COMBINE = 31;
    private static final int SLOT_BACK = 35;

    private final @Nullable GUI returnTo;

    public ShardsGUI(Player player, @Nullable GUI returnTo) {
        super(player, 36, GuiText.raw("gui.shards.title"));
        this.returnTo = returnTo;
    }

    /** Shared post-redemption announcement and sound used by all three shard kinds. */
    static void announceWon(Player player, SkinShards.PoolEntry won, Rarity rarity) {
        SoundBuilder.of(Sound.UI_TOAST_CHALLENGE_COMPLETE).pitch(1.1f).play(player);
        player.sendMessage(GameConfigManager.getMessage("shards.consumed", Map.of(
                "skin", won.skin().getDisplayName(),
                "hero", HeroConfig.formatName(won.heroId()),
                "rarity", SkinShards.formatRarity(rarity))));
    }

    @Override
    protected void populate() {
        inventoryItems.clear();
        PlayerStats stats = PlayerStatsManager.getStats(player);

        int normalPool = SkinShards.eligiblePool(player, Rarity.NORMAL).size();
        int rarePool = SkinShards.eligiblePool(player, Rarity.RARE).size();
        int legendaryPool = SkinShards.eligiblePool(player, Rarity.LEGENDARY).size();

        inventoryItems.put(SLOT_RANDOM_NORMAL, shardItem(stats, ShardKind.RANDOM, Rarity.NORMAL, Material.QUARTZ, normalPool));
        inventoryItems.put(SLOT_RANDOM_RARE, shardItem(stats, ShardKind.RANDOM, Rarity.RARE, Material.PRISMARINE_SHARD, rarePool));
        inventoryItems.put(SLOT_RANDOM_LEGENDARY, shardItem(stats, ShardKind.RANDOM, Rarity.LEGENDARY, Material.AMETHYST_SHARD, legendaryPool));

        inventoryItems.put(SLOT_HERO_NORMAL, shardItem(stats, ShardKind.HERO_SELECT, Rarity.NORMAL, Material.IRON_INGOT, normalPool));
        inventoryItems.put(SLOT_HERO_RARE, shardItem(stats, ShardKind.HERO_SELECT, Rarity.RARE, Material.DIAMOND, rarePool));
        inventoryItems.put(SLOT_HERO_LEGENDARY, shardItem(stats, ShardKind.HERO_SELECT, Rarity.LEGENDARY, Material.NETHER_STAR, legendaryPool));

        inventoryItems.put(SLOT_SKIN_NORMAL, shardItem(stats, ShardKind.SKIN_SELECT, Rarity.NORMAL, Material.PAPER, normalPool));
        inventoryItems.put(SLOT_SKIN_RARE, shardItem(stats, ShardKind.SKIN_SELECT, Rarity.RARE, Material.ENCHANTED_BOOK, rarePool));
        inventoryItems.put(SLOT_SKIN_LEGENDARY, shardItem(stats, ShardKind.SKIN_SELECT, Rarity.LEGENDARY, Material.TOTEM_OF_UNDYING, legendaryPool));

        inventoryItems.put(SLOT_COMBINE, ItemBuilder.from(Material.ANVIL)
                .name(GuiText.component("gui.shards.combine-button-name"))
                .lore(GuiText.lore("gui.shards.combine-button-lore"))
                .build());

        inventoryItems.put(SLOT_BACK, backItem());
    }

    private ItemStack shardItem(PlayerStats stats, ShardKind kind, Rarity rarity, Material material, int poolSize) {
        String rarityKey = rarity.name().toLowerCase(Locale.ROOT);
        int count = stats.getShardCount(kind, rarity);

        String nameKey = switch (kind) {
            case RANDOM -> "gui.shards.rarity-" + rarityKey + "-name";
            case HERO_SELECT -> "gui.shards.hero-select-" + rarityKey + "-name";
            case SKIN_SELECT -> "gui.shards.skin-select-" + rarityKey + "-name";
        };
        String actionKey = switch (kind) {
            case RANDOM -> "gui.shards.consume-lore";
            case HERO_SELECT -> "gui.shards.open-hero-select-lore";
            case SKIN_SELECT -> "gui.shards.open-skin-select-lore";
        };

        List<Component> lore = new ArrayList<>();
        lore.add(GuiText.component("gui.shards.owned-lore", "count", count));
        lore.add(GuiText.component("gui.shards.pool-lore", "pool", poolSize));
        lore.add(Component.empty());
        if (count <= 0) {
            lore.add(GuiText.component("gui.shards.none-lore"));
        } else if (poolSize <= 0) {
            lore.add(GuiText.component("gui.shards.empty-pool-lore"));
        } else {
            lore.add(GuiText.component(actionKey));
        }

        return ItemBuilder.from(material)
                .amount(Math.max(1, Math.min(64, count)))
                .name(GuiText.component(nameKey))
                .lore(lore)
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_RANDOM_NORMAL -> consumeRandom(Rarity.NORMAL);
            case SLOT_RANDOM_RARE -> consumeRandom(Rarity.RARE);
            case SLOT_RANDOM_LEGENDARY -> consumeRandom(Rarity.LEGENDARY);

            case SLOT_HERO_NORMAL -> openSelection(ShardKind.HERO_SELECT, Rarity.NORMAL);
            case SLOT_HERO_RARE -> openSelection(ShardKind.HERO_SELECT, Rarity.RARE);
            case SLOT_HERO_LEGENDARY -> openSelection(ShardKind.HERO_SELECT, Rarity.LEGENDARY);

            case SLOT_SKIN_NORMAL -> openSelection(ShardKind.SKIN_SELECT, Rarity.NORMAL);
            case SLOT_SKIN_RARE -> openSelection(ShardKind.SKIN_SELECT, Rarity.RARE);
            case SLOT_SKIN_LEGENDARY -> openSelection(ShardKind.SKIN_SELECT, Rarity.LEGENDARY);

            case SLOT_COMBINE -> GUIManager.open(player, new ShardCombineGUI(player, this));
            case SLOT_BACK -> {
                if (returnTo != null) GUIManager.open(player, returnTo);
                else GUIManager.close(player);
            }
        }
    }

    private void consumeRandom(Rarity rarity) {
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
                announceWon(player, result.won(), rarity);
                populate();
                update();
            }
        }
    }

    private void openSelection(ShardKind kind, Rarity rarity) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        String rarityName = SkinShards.formatRarity(rarity);
        if (stats.getShardCount(kind, rarity) <= 0) {
            playError();
            player.sendMessage(GameConfigManager.getMessage("shards.none-kind", Map.of(
                    "rarity", rarityName,
                    "kind", kind.displayName())));
            return;
        }
        if (SkinShards.eligiblePool(player, rarity).isEmpty()) {
            playError();
            player.sendMessage(GameConfigManager.getMessage("shards.empty-pool", Map.of("rarity", rarityName)));
            return;
        }
        GUI picker = kind == ShardKind.HERO_SELECT
                ? new ShardHeroSelectGUI(player, rarity, this)
                : new ShardSkinSelectGUI(player, rarity, this);
        GUIManager.open(player, picker);
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
