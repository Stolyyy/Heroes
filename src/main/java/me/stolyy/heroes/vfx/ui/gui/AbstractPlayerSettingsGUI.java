package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.rules.GameModePreset;
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

import java.util.function.Consumer;

public abstract class AbstractPlayerSettingsGUI extends GUI {
    protected static final int SLOT_LIVES = 9;
    protected static final int SLOT_HEALTH = 11;
    protected static final int SLOT_ULTIMATES = 13;
    protected static final int SLOT_FRIENDLY_FIRE = 15;
    protected static final int SLOT_RANDOM_HEROES = 17;

    protected final CustomGameGUI partyGUI;
    protected final PlayerSettings settings;
    protected final Game game;

    public AbstractPlayerSettingsGUI(Player player, int size, String title, PlayerSettings settings, CustomGameGUI partyGUI) {
        super(player, size, title);
        this.settings = settings;
        this.partyGUI = partyGUI;
        this.game = partyGUI != null ? partyGUI.getGame() : null;
    }

    /**
     * Applies one settings change at the scope this GUI edits. The base implementation
     * writes to {@code settings} directly — the per-player scope, where {@code settings}
     * is the target GamePlayer's own copy. Team- and game-scope GUIs override to fan the
     * change out to every affected GamePlayer.
     */
    protected void applySetting(Consumer<PlayerSettings> change) {
        change.accept(settings);
    }

    protected boolean isGameStillWaiting() {
        return game != null && game.gameState() == GameState.WAITING;
    }

    /** Lives are inert in endless-respawn modes (TDM/Evolution) — hide the item there. */
    protected boolean showsLives() {
        return game == null || game.modeConfig().deathOutcome().consumesLife();
    }

    /** Evolution forces its own hero order, so the random-heroes toggle means nothing. */
    protected boolean showsRandomHeroes() {
        return game == null || game.gameModePreset() != GameModePreset.EVOLUTION;
    }

    @Override
    protected void update() {
        if (showsLives()) {
            inventoryItems.put(SLOT_LIVES, ItemBuilder.from(Material.PAPER)
                    .name("<gold>" + settings.lives() + " Lives")
                    .lore(Component.text("Left-click: cycle", NamedTextColor.GRAY),
                          Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                    .modelData("gui", "settings", "lives")
                    .build());
        } else {
            inventoryItems.remove(SLOT_LIVES);
        }

        inventoryItems.put(SLOT_HEALTH, ItemBuilder.from(Material.GOLDEN_APPLE)
                .name("<gold>Starting Health: " + (int) settings.health())
                .lore(Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount (0-1000)", NamedTextColor.GRAY))
                .modelData("gui", "settings", "health")
                .build());

        inventoryItems.put(SLOT_ULTIMATES, settings.ultimatesEnabled()
                ? ItemBuilder.from(Material.NETHER_STAR)
                    .name("<green>Ultimates: Enabled")
                    .modelData("gui", "settings", "ultimate-on")
                    .build()
                : ItemBuilder.from(Material.COAL)
                    .name("<red>Ultimates: Disabled")
                    .modelData("gui", "settings", "ultimate-off")
                    .build());

        inventoryItems.put(SLOT_FRIENDLY_FIRE, settings.friendlyFire()
                ? ItemBuilder.from(Material.GOLDEN_SWORD)
                    .name("<green>Friendly Fire: Enabled")
                    .modelData("gui", "settings", "friendly-on")
                    .build()
                : ItemBuilder.from(Material.STONE_SWORD)
                    .name("<red>Friendly Fire: Disabled")
                    .modelData("gui", "settings", "friendly-off")
                    .build());

        if (showsRandomHeroes()) {
            inventoryItems.put(SLOT_RANDOM_HEROES, settings.randomizeHeroes()
                    ? ItemBuilder.from(Material.PAPER)
                        .name("<green>Random Heroes: Enabled")
                        .modelData("gui", "settings", "random-on")
                        .build()
                    : ItemBuilder.from(Material.PAPER)
                        .name("<red>Random Heroes: Disabled")
                        .modelData("gui", "settings", "random-off")
                        .build());
        } else {
            inventoryItems.remove(SLOT_RANDOM_HEROES);
        }

        fillEmptySlots();
        super.update();
    }

    protected void handleLivesClick(ClickType click) {
        if (!showsLives()) return; // slot is filler in endless-respawn modes
        if (!isGameStillWaiting()) { player.closeInventory(); return; }
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the number of lives (1-20):", 1, 20,
                    value -> {
                        applySetting(s -> s.setLives(value));
                        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        int newLives = switch (settings.lives()) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 1;
            default -> 3;
        };
        applySetting(s -> s.setLives(newLives));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    protected void handleHealthClick(ClickType click) {
        if (!isGameStillWaiting()) { player.closeInventory(); return; }
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the starting health (0-1000):", 0, 1000,
                    value -> {
                        applySetting(s -> s.setHealth(value));
                        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        double newHealth = switch ((int) settings.health()) {
            case 50 -> 100;
            case 100 -> 150;
            case 150 -> 200;
            case 200 -> 300;
            default -> 50;
        };
        applySetting(s -> s.setHealth(newHealth));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    protected void handleUltimatesClick() {
        if (!isGameStillWaiting()) { player.closeInventory(); return; }
        boolean newValue = !settings.ultimatesEnabled();
        applySetting(s -> s.setUltimatesEnabled(newValue));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    protected void handleFriendlyFireClick() {
        if (!isGameStillWaiting()) { player.closeInventory(); return; }
        boolean newValue = !settings.friendlyFire();
        applySetting(s -> s.setFriendlyFire(newValue));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    protected void handleRandomHeroesClick() {
        if (!showsRandomHeroes()) return; // slot is filler in Evolution
        if (!isGameStillWaiting()) { player.closeInventory(); return; }
        boolean newValue = !settings.randomizeHeroes();
        applySetting(s -> s.setRandomizeHeroes(newValue));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    @Override
    protected GUI getParentGUI() {
        return partyGUI;
    }
}
