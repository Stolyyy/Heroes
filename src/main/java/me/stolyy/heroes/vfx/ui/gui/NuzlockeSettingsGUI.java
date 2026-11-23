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

/** Nuzlocke settings (B13.5/B13.7): stocks per round, HP carry-over, lock-in, heroes to eliminate. */
public class NuzlockeSettingsGUI extends GUI {
    private static final int SLOT_STOCKS = 10;
    private static final int SLOT_HP_CARRYOVER = 12;
    private static final int SLOT_LOCK_IN = 14;
    private static final int SLOT_HEROES_TO_ELIMINATE = 16;
    private static final int SLOT_BACK = 22;
    private static final int SLOT_TEAM_SCOPE = 4;

    private final Game game;
    private final CustomGameGUI partyGUI;

    public NuzlockeSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(player, 27, "Nuzlocke Settings");
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

        inventoryItems.put(SLOT_HP_CARRYOVER, game.modeConfig().hpCarryover()
                ? ItemBuilder.from(Material.GOLDEN_APPLE)
                    .name("<green>HP Carry-Over: Enabled")
                    .lore(Component.text("Surviving heroes keep their HP", NamedTextColor.GRAY),
                          Component.text("into the next round", NamedTextColor.GRAY))
                    .build()
                : ItemBuilder.from(Material.APPLE)
                    .name("<red>HP Carry-Over: Disabled")
                    .build());

        inventoryItems.put(SLOT_LOCK_IN, game.modeConfig().lockIn()
                ? ItemBuilder.from(Material.IRON_DOOR)
                    .name("<green>Lock-In: Enabled")
                    .lore(Component.text("You keep playing your hero", NamedTextColor.GRAY),
                          Component.text("until it dies", NamedTextColor.GRAY))
                    .build()
                : ItemBuilder.from(Material.OAK_DOOR)
                    .name("<red>Lock-In: Disabled")
                    .build());

        String target = game.modeConfig().heroesToEliminate();
        inventoryItems.put(SLOT_TEAM_SCOPE, game.modeConfig().seriesTeamScope()
                ? ItemBuilder.from(Material.LEAD)
                    .name("<green>Team Exhaustion: Enabled")
                    .lore(Component.text("One player out of heroes puts", NamedTextColor.GRAY),
                          Component.text("their whole team out.", NamedTextColor.GRAY))
                    .build()
                : ItemBuilder.from(Material.ENDER_PEARL)
                    .name("<red>Team Exhaustion: Disabled")
                    .lore(Component.text("Players drop out individually.", NamedTextColor.GRAY))
                    .build());

        inventoryItems.put(SLOT_HEROES_TO_ELIMINATE, ItemBuilder.from(Material.SKELETON_SKULL)
                .name("<gold>Heroes To Eliminate: " + ("all".equalsIgnoreCase(target) ? "All" : target))
                .lore(Component.text("Lose this many heroes and", NamedTextColor.GRAY),
                      Component.text("you're out of the series", NamedTextColor.GRAY),
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
            case SLOT_HP_CARRYOVER -> {
                game.setModeConfig(game.modeConfig().withHpCarryover(!game.modeConfig().hpCarryover()));
                clickSound();
                update();
            }
            case SLOT_LOCK_IN -> {
                game.setModeConfig(game.modeConfig().withLockIn(!game.modeConfig().lockIn()));
                clickSound();
                update();
            }
            case SLOT_HEROES_TO_ELIMINATE -> handleHeroesToEliminate(event.getClick());
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

    private void handleHeroesToEliminate(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the number of heroes to eliminate (1-50):", 1, 50,
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
