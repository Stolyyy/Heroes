package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameBot;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Lobby configuration menu for an individual custom-game bot (hero, difficulty tier, removal). */
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
        super(player, 27, GuiText.raw("gui.bot.title", "bot", bot.getName()));
        this.game = game;
        this.bot = bot;
        this.botUUID = botUUID;
        this.parentGUI = parentGUI;
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_CHANGE_HERO, ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
                .name(GuiText.component("gui.bot.change-hero-name"))
                .lore(GuiText.lore("gui.bot.change-hero-lore"))
                .modelData("gui", "bot", "change-hero")
                .build());

        inventoryItems.put(SLOT_REMOVE_BOT, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(GuiText.component("gui.bot.remove-name"))
                .modelData("gui", "bot", "remove")
                .build());

        inventoryItems.put(SLOT_BACK, backItem());
    }

    @Override
    protected void update() {
        inventoryItems.put(SLOT_HERO_DISPLAY, HeroItems.heroHead(
                bot.getHeroName(),
                GuiText.component("gui.bot.hero-display-name", "hero", bot.getHeroName()),
                GuiText.lore("gui.bot.hero-display-lore")));
        inventoryItems.put(SLOT_DIFFICULTY, createDifficultyItem());
        fillEmptySlots();
        super.update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (game.gameState() != GameState.WAITING) {
            player.closeInventory();
            return;
        }
        switch (event.getSlot()) {
            case SLOT_CHANGE_HERO -> GUIManager.open(player,
                    HeroSelectGUI.forBot(player, bot.getName(), bot::setHeroName, this));
            case SLOT_DIFFICULTY -> handleDifficultyClick(event.isLeftClick());
            case SLOT_REMOVE_BOT -> handleRemoveBot();
            case SLOT_BACK -> GUIManager.open(player, parentGUI);
        }
    }

    private void handleDifficultyClick(boolean increase) {
        int tier = bot.getDifficultyTier() + (increase ? 1 : -1);
        if (tier > MAX_TIER) tier = MIN_TIER;
        if (tier < MIN_TIER) tier = MAX_TIER;
        bot.setDifficultyTier(tier);
        playConfirm();
        update();
    }

    private void handleRemoveBot() {
        if (botUUID != null) {
            game.removeBot(botUUID);
        }
        parentGUI.update();
        GUIManager.open(player, parentGUI);
    }

    private ItemStack createDifficultyItem() {
        String bar = "■".repeat(bot.getDifficultyTier())
                + "□".repeat(MAX_TIER - bot.getDifficultyTier());
        return ItemBuilder.from(Material.CLOCK)
                .name(GuiText.component("gui.bot.difficulty-name", "tier", bot.getDifficultyTier(), "bar", bar))
                .lore(GuiText.lore("gui.bot.difficulty-lore"))
                .modelData("gui", "bot", "difficulty")
                .build();
    }

    @Override
    protected GUI getParentGUI() {
        return parentGUI;
    }
}
