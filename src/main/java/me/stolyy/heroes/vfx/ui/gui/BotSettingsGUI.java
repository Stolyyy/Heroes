package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameBot;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * GUI for configuring an individual bot in the custom game lobby.
 * Allows changing hero and removing the bot.
 */
public class BotSettingsGUI extends GUI {
    private static final int SLOT_HERO_DISPLAY = 4;
    private static final int SLOT_CHANGE_HERO = 11;
    private static final int SLOT_DIFFICULTY = 13;
    private static final int SLOT_REMOVE_BOT = 15;
    private static final int SLOT_BACK = 22;
    private static final int MIN_TIER = 1;
    private static final int MAX_TIER = 5;

    private final Game game;
    private final GameBot bot;
    private final UUID botUUID;
    private final CustomGameGUI parentGUI;

    public BotSettingsGUI(Game game, GameBot bot, UUID botUUID, Player player, CustomGameGUI parentGUI) {
        super(player, 27, "Bot: " + bot.getName());
        this.game = game;
        this.bot = bot;
        this.botUUID = botUUID;
        this.parentGUI = parentGUI;
        update();
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_CHANGE_HERO, ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
                .name("<green>Change Hero")
                .lore(Component.text("Opens the hero picker", NamedTextColor.GRAY))
                .modelData("gui", "bot", "change-hero")
                .build());

        inventoryItems.put(SLOT_REMOVE_BOT, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name("<red>Remove Bot")
                .modelData("gui", "bot", "remove")
                .build());

        inventoryItems.put(SLOT_BACK, ItemBuilder.from(Material.PAPER)
                .name("<white>Back")
                .modelData("gui", "navigation", "back")
                .build());
    }

    @Override
    protected void update() {
        inventoryItems.put(SLOT_HERO_DISPLAY, createHeroDisplay());
        inventoryItems.put(SLOT_DIFFICULTY, createDifficultyItem());
        fillEmptySlots();
        super.update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_CHANGE_HERO -> handleChangeHero();
            case SLOT_DIFFICULTY -> handleDifficultyClick(event.isLeftClick());
            case SLOT_REMOVE_BOT -> handleRemoveBot();
            case SLOT_BACK -> GUIManager.open(player, parentGUI);
        }
    }

    /** Left-click +1, right-click -1, wrapping 1-5. Applied when the AI is built at game start. */
    private void handleDifficultyClick(boolean increase) {
        int tier = bot.getDifficultyTier() + (increase ? 1 : -1);
        if (tier > MAX_TIER) tier = MIN_TIER;
        if (tier < MIN_TIER) tier = MAX_TIER;
        bot.setDifficultyTier(tier);
        update();
    }

    /**
     * Same picker players get for their own hero override (PlayerSettingsGUI) — index-cycling
     * through the whole roster one click at a time was unusable at 19 heroes. Returning through
     * GUIManager.open re-runs populate()/update(), so the hero display refreshes itself.
     * Heroes bots can't play (bots.yml disabled_heroes) are filtered out of the picker.
     */
    private void handleChangeHero() {
        GUIManager.open(player, new HeroSelectGUI(player,
                "Hero: " + bot.getName(),
                name -> !me.stolyy.heroes.util.BotConfig.isDisabledForBots(name),
                null,      // no eliminated set
                bot::setHeroName,
                null,      // no reset item: a bot has no underlying lobby pick to fall back to
                this,
                null));    // not a series pick
    }

    private void handleRemoveBot() {
        if (botUUID != null) {
            game.removeBot(botUUID);
        }
        // Refresh parent and return to it
        parentGUI.update();
        GUIManager.open(player, parentGUI);
    }

    private ItemStack createDifficultyItem() {
        String bar = "■".repeat(bot.getDifficultyTier())
                + "□".repeat(MAX_TIER - bot.getDifficultyTier());
        return ItemBuilder.from(Material.CLOCK)
                .name("<yellow>Difficulty: " + bot.getDifficultyTier() + " <gray>" + bar)
                .lore(Component.text("Left-click +1, right-click -1", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Higher = faster reactions (bots.yml tiers)", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false))
                .modelData("gui", "bot", "difficulty")
                .build();
    }

    private ItemStack createHeroDisplay() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(bot.getHeroName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Current Hero", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Click 'Change Hero' to pick", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
        }
        // Show the bot's hero head art (per-hero sponge.json keys)
        ItemBuilder.applyModelData(item, bot.getHeroName(), "head", "default");
        return item;
    }

    @Override
    protected GUI getParentGUI() {
        return parentGUI;
    }
}
