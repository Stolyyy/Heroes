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

/** Ironman settings (B13.6/B13.7): stocks per round + heroes to clear. Edits the game's modeConfig. */
public class IronmanSettingsGUI extends GUI {
    private static final int SLOT_STOCKS = 11;
    private static final int SLOT_HEROES_TO_CLEAR = 15;
    private static final int SLOT_BACK = 22;
    private static final int SLOT_TEAM_SCOPE = 13;

    private final Game game;
    private final CustomGameGUI partyGUI;

    public IronmanSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(player, 27, "Ironman Settings");
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
        inventoryItems.put(SLOT_STOCKS, ItemBuilder.from(Material.PAPER)
                .name("<gold>Stocks Per Round: " + game.modeConfig().stocks())
                .lore(Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        String target = game.modeConfig().heroesToEliminate();
        inventoryItems.put(SLOT_TEAM_SCOPE, game.modeConfig().seriesTeamScope()
                ? ItemBuilder.from(Material.TOTEM_OF_UNDYING)
                    .name("<green>Win Scope: Whole Team")
                    .lore(Component.text("Every member of a team must", NamedTextColor.GRAY),
                          Component.text("clear their heroes to win.", NamedTextColor.GRAY))
                    .build()
                : ItemBuilder.from(Material.GOLD_NUGGET)
                    .name("<red>Win Scope: First Player")
                    .lore(Component.text("First player to clear wins the", NamedTextColor.GRAY),
                          Component.text("series for their team.", NamedTextColor.GRAY))
                    .build());

        inventoryItems.put(SLOT_HEROES_TO_CLEAR, ItemBuilder.from(Material.GOLDEN_HELMET)
                .name("<gold>Heroes To Clear: " + ("all".equalsIgnoreCase(target) ? "All" : target))
                .lore(Component.text("Win a round with this many", NamedTextColor.GRAY),
                      Component.text("heroes to win the series", NamedTextColor.GRAY),
                      Component.text("Left-click: cycle | Right-click: enter", NamedTextColor.DARK_GRAY))
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
            case SLOT_STOCKS -> handleStocks(event.getClick());
            case SLOT_HEROES_TO_CLEAR -> handleHeroesToClear(event.getClick());
            case SLOT_TEAM_SCOPE -> {
                game.setModeConfig(game.modeConfig().withSeriesTeamScope(!game.modeConfig().seriesTeamScope()));
                SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
                update();
            }
            case SLOT_BACK -> GUIManager.open(player, partyGUI);
        }
    }

    private void handleStocks(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter stocks per round (1-10):", 1, 10,
                    value -> {
                        game.setModeConfig(game.modeConfig().withStocks(value));
                        clickSound();
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        int newStocks = switch (game.modeConfig().stocks()) {
            case 1 -> 2;
            case 2 -> 3;
            default -> 1;
        };
        game.setModeConfig(game.modeConfig().withStocks(newStocks));
        clickSound();
        update();
    }

    private void handleHeroesToClear(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the number of heroes to clear (1-50):", 1, 50,
                    value -> {
                        game.setModeConfig(game.modeConfig().withHeroesToEliminate(String.valueOf(value)));
                        clickSound();
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        String current = game.modeConfig().heroesToEliminate();
        String next = switch (current.toLowerCase()) {
            case "all" -> "3";
            case "3" -> "5";
            case "5" -> "7";
            default -> "all";
        };
        game.setModeConfig(game.modeConfig().withHeroesToEliminate(next));
        clickSound();
        update();
    }

    private void clickSound() {
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
    }

    @Override
    protected GUI getParentGUI() {
        return partyGUI;
    }
}
