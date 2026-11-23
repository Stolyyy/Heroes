package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.prompt.PromptManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.function.Consumer;

public class GameSettingsGUI extends AbstractPlayerSettingsGUI {
    private static final int SLOT_CRYSTALS = 28;
    private static final int SLOT_WALLS = 32;
    private static final int SLOT_TIMER = 34;
    private static final int SLOT_CLOSE = 49;
    private static final int TIMER_3MIN = 180;
    private static final int TIMER_5MIN = 300;
    private static final int TIMER_7MIN = 420;
    private static final int TIMER_10MIN = 600;

    public GameSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(player, 54, "Game Settings", game.defaultTeamSettings(), partyGUI);
        update();
    }

    /**
     * Game scope: every change goes to the game defaults (the display object) AND all
     * current GamePlayers — applied live, so per-team/per-player edits made afterwards
     * are never clobbered by a save-on-close.
     */
    @Override
    protected void applySetting(Consumer<PlayerSettings> change) {
        game.applySettingsAll(change);
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_CLOSE, ItemBuilder.from(Material.PAPER)
                .name("<red>Save & Close")
                .modelData("gui", "general", "close")
                .build());
    }

    @Override
    protected void update() {
        inventoryItems.put(SLOT_CRYSTALS, game.settings().smashCrystals()
                ? ItemBuilder.from(Material.END_CRYSTAL)
                    .name("<green>Smash Crystals: Enabled (" + game.settings().crystalInterval() + "s)")
                    .lore(Component.text("Left-click: toggle", NamedTextColor.GRAY),
                          Component.text("Right-click: set spawn interval", NamedTextColor.GRAY))
                    .modelData("gui", "settings", "crystals-on")
                    .build()
                : ItemBuilder.from(Material.GLASS)
                    .name("<red>Smash Crystals: Disabled")
                    .lore(Component.text("Left-click: toggle", NamedTextColor.GRAY),
                          Component.text("Right-click: set spawn interval", NamedTextColor.GRAY))
                    .modelData("gui", "settings", "crystals-off")
                    .build());

        inventoryItems.put(SLOT_WALLS, game.settings().killWalls()
                ? ItemBuilder.from(Material.RED_SANDSTONE_WALL)
                    .name("<green>Kill Walls: Enabled")
                    .modelData("gui", "settings", "walls-on")
                    .build()
                : ItemBuilder.from(Material.SANDSTONE_WALL)
                    .name("<red>Kill Walls: Disabled")
                    .modelData("gui", "settings", "walls-off")
                    .build());

        inventoryItems.put(SLOT_TIMER, ItemBuilder.from(Material.CLOCK)
                .name("<gold>Match Timer: " + formatTime(game.settings().timer()))
                .lore(Component.text("Left-click: cycle presets", NamedTextColor.GRAY),
                      Component.text("Right-click: enter seconds", NamedTextColor.GRAY))
                .modelData("gui", "settings", "timer")
                .build());

        super.update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_LIVES -> handleLivesClick(event.getClick());
            case SLOT_HEALTH -> handleHealthClick(event.getClick());
            case SLOT_ULTIMATES -> handleUltimatesClick();
            case SLOT_FRIENDLY_FIRE -> handleFriendlyFireClick();
            case SLOT_RANDOM_HEROES -> handleRandomHeroesClick();
            case SLOT_CRYSTALS -> handleCrystalsClick(event.getClick());
            case SLOT_WALLS -> handleWallsClick();
            case SLOT_TIMER -> handleTimerClick(event.getClick());
            case SLOT_CLOSE -> GUIManager.open(player, partyGUI);
        }
    }

    private void handleCrystalsClick(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the crystal spawn interval in seconds (10-600):", 10, 600,
                    value -> {
                        game.settings().setCrystalInterval(value);
                        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        game.settings().setSmashCrystals(!game.settings().smashCrystals());
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    private void handleWallsClick() {
        game.settings().toggleKillWalls();
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    private void handleTimerClick(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the match timer in seconds (60-3600):", 60, 3600,
                    value -> {
                        game.settings().setTimer(value);
                        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        int newTime = switch (game.settings().timer()) {
            case TIMER_3MIN -> TIMER_5MIN;
            case TIMER_5MIN -> TIMER_7MIN;
            case TIMER_7MIN -> TIMER_10MIN;
            case TIMER_10MIN -> TIMER_3MIN;
            default -> TIMER_7MIN;
        };
        game.settings().setTimer(newTime);
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + ":" + String.format("%02d", secs);
    }
}
