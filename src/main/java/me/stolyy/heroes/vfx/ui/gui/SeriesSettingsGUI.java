package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameState;
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

/** Plain best-of-X series settings: round target + hero re-pick between rounds. */
public class SeriesSettingsGUI extends GUI {
    private static final int SLOT_BEST_OF = 12;
    private static final int SLOT_REPICK = 14;
    private static final int SLOT_BACK = 22;

    private final Game game;
    private final CustomGameGUI partyGUI;

    public SeriesSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(player, 27, "Series Settings");
        this.game = game;
        this.partyGUI = partyGUI;
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_BACK, ItemBuilder.from(Material.PAPER)
                .name("<white>Save & Back")
                .modelData("gui", "navigation", "back")
                .build());
    }

    @Override
    protected void update() {
        int bestOf = game.modeConfig().bestOf();
        inventoryItems.put(SLOT_BEST_OF, ItemBuilder.from(Material.CLOCK)
                .name("<gold>Best of: " + bestOf + " (first to " + (bestOf / 2 + 1) + ")")
                .lore(Component.text("Left-click: cycle 3/5/7", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        boolean repick = game.modeConfig().repickBetweenRounds();
        inventoryItems.put(SLOT_REPICK, repick
                ? ItemBuilder.from(Material.ENDER_PEARL)
                    .name("<green>Hero Re-Pick Between Rounds: Enabled")
                    .build()
                : ItemBuilder.from(Material.ENDER_EYE)
                    .name("<red>Hero Re-Pick Between Rounds: Disabled")
                    .build());

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
            case SLOT_BEST_OF -> handleBestOf(event.getClick());
            case SLOT_REPICK -> {
                game.setModeConfig(game.modeConfig()
                        .withRepickBetweenRounds(!game.modeConfig().repickBetweenRounds()));
                SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
                update();
            }
            case SLOT_BACK -> GUIManager.open(player, partyGUI);
        }
    }

    private void handleBestOf(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the series length (1-15, odd recommended):", 1, 15,
                    value -> {
                        game.setModeConfig(game.modeConfig().withBestOf(value));
                        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        int newBestOf = switch (game.modeConfig().bestOf()) {
            case 3 -> 5;
            case 5 -> 7;
            default -> 3;
        };
        game.setModeConfig(game.modeConfig().withBestOf(newBestOf));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    @Override
    protected GUI getParentGUI() {
        return partyGUI;
    }
}
