package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Main lobby menu opened from the announcer NPC. */
public class LobbyMenuGUI extends GUI {
    private static final int SLOT_ONE_V_ONE = 10;
    private static final int SLOT_TWO_V_TWO = 11;
    private static final int SLOT_CUSTOM_GAME = 12;
    private static final int SLOT_HERO_SELECT = 13;
    private static final int SLOT_SETTINGS = 14;

    public LobbyMenuGUI(Player player) {
        super(player, 27, GuiText.raw("gui.lobby-menu.title"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_ONE_V_ONE -> {
                GUIManager.close(player);
                toggleQueue(GameMode.ONE_V_ONE);
            }
            case SLOT_TWO_V_TWO -> {
                GUIManager.close(player);
                toggleQueue(GameMode.TWO_V_TWO);
            }
            case SLOT_CUSTOM_GAME -> {
                GUIManager.close(player);
                GameManager.joinGame(player, GameMode.CUSTOM);
            }
            case SLOT_HERO_SELECT -> GUIManager.open(player, HeroSelectGUI.lobby(player));
            case SLOT_SETTINGS -> {
                player.sendMessage(GuiText.component("gui.lobby-menu.coming-soon"));
                SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.5f).play(player);
            }
        }
    }

    private void toggleQueue(GameMode mode) {
        if (QueueManager.isQueuedFor(player, mode)) {
            QueueManager.leaveQueue(player, mode);
        } else {
            QueueManager.joinQueue(player, mode);
        }
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_ONE_V_ONE, ItemBuilder.from(Material.IRON_SWORD)
                .name(GuiText.component("gui.lobby-menu.1v1-name"))
                .lore(GuiText.lore("gui.lobby-menu.1v1-lore"))
                .modelData("gui", "lobby", "1v1")
                .build());

        inventoryItems.put(SLOT_TWO_V_TWO, ItemBuilder.from(Material.IRON_SWORD)
                .name(GuiText.component("gui.lobby-menu.2v2-name"))
                .amount(2)
                .lore(GuiText.lore("gui.lobby-menu.2v2-lore"))
                .modelData("gui", "lobby", "2v2")
                .build());

        inventoryItems.put(SLOT_CUSTOM_GAME, ItemBuilder.from(Material.NETHER_STAR)
                .name(GuiText.component("gui.lobby-menu.custom-name"))
                .lore(GuiText.lore("gui.lobby-menu.custom-lore"))
                .modelData("gui", "lobby", "custom")
                .build());

        inventoryItems.put(SLOT_HERO_SELECT, ItemBuilder.from(Material.PLAYER_HEAD)
                .name(GuiText.component("gui.lobby-menu.hero-select-name"))
                .lore(GuiText.lore("gui.lobby-menu.hero-select-lore"))
                .modelData("gui", "lobby", "hero-select")
                .build());

        inventoryItems.put(SLOT_SETTINGS, ItemBuilder.from(Material.REDSTONE)
                .name(GuiText.component("gui.lobby-menu.settings-name"))
                .lore(GuiText.lore("gui.lobby-menu.settings-lore"))
                .modelData("gui", "lobby", "settings")
                .build());
    }
}
