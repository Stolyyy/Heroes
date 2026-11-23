package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class AnnouncerGUI extends GUI {
    private static final int SLOT_ONE_V_ONE = 10;
    private static final int SLOT_TWO_V_TWO = 11;
    private static final int SLOT_CUSTOM_GAME = 12;
    private static final int SLOT_HERO_SELECT = 13;
    private static final int SLOT_SETTINGS = 14;

    public AnnouncerGUI(Player player) {
        super(player, 27, "Lobby Menu");
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
            case SLOT_HERO_SELECT -> GUIManager.open(player, new HeroSelectGUI(player));
            case SLOT_SETTINGS -> {
                player.sendMessage(Component.text("Coming Soon!", NamedTextColor.YELLOW));
                SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.5f).play(player);
            }
        }
    }

    /** The queue buttons toggle: already waiting in that mode's queue → clicking leaves it. */
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
                .name("<gold>1v1 Queue")
                .lore(
                        Component.text("Battle another player in a", NamedTextColor.GRAY),
                        Component.text("1 versus 1 ranked match!", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to join queue!", NamedTextColor.YELLOW)
                )
                .modelData("gui", "lobby", "1v1")
                .build());

        inventoryItems.put(SLOT_TWO_V_TWO, ItemBuilder.from(Material.IRON_SWORD)
                .name("<gold>2v2 Queue")
                .amount(2)
                .lore(
                        Component.text("Team up with a friend and", NamedTextColor.GRAY),
                        Component.text("fight another duo!", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Solo or party of 2!", NamedTextColor.GRAY),
                        Component.text("Click to join queue!", NamedTextColor.YELLOW)
                )
                .modelData("gui", "lobby", "2v2")
                .build());

        inventoryItems.put(SLOT_CUSTOM_GAME, ItemBuilder.from(Material.NETHER_STAR)
                .name("<gold>Custom Game")
                .lore(
                        Component.text("Create a custom game with", NamedTextColor.GRAY),
                        Component.text("your party members!", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Requires a party!", NamedTextColor.RED),
                        Component.text("Click to create!", NamedTextColor.YELLOW)
                )
                .modelData("gui", "lobby", "custom")
                .build());

        inventoryItems.put(SLOT_HERO_SELECT, ItemBuilder.from(Material.PLAYER_HEAD)
                .name("<gold>Select Hero")
                .lore(
                        Component.text("Choose your hero for", NamedTextColor.GRAY),
                        Component.text("upcoming battles!", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to select!", NamedTextColor.YELLOW)
                )
                .modelData("gui", "lobby", "hero-select")
                .build());

        inventoryItems.put(SLOT_SETTINGS, ItemBuilder.from(Material.REDSTONE)
                .name("<gold>Settings")
                .lore(
                        Component.text("Configure your preferences", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to open!", NamedTextColor.YELLOW)
                )
                .modelData("gui", "lobby", "settings")
                .build());
    }
}
