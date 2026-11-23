package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.rules.RespawnAction;
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

/** TDM settings (B13.7): kill limit, start HP, hero re-pick on respawn. Edits the game's modeConfig. */
public class TdmSettingsGUI extends GUI {
    private static final int SLOT_KILL_LIMIT = 11;
    private static final int SLOT_START_HP = 13;
    private static final int SLOT_HERO_REPICK = 15;
    private static final int SLOT_ULT_CARRYOVER = 17;
    private static final int SLOT_BACK = 22;

    private final Game game;
    private final CustomGameGUI partyGUI;

    public TdmSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(player, 27, "Team Deathmatch Settings");
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
        inventoryItems.put(SLOT_KILL_LIMIT, ItemBuilder.from(Material.IRON_SWORD)
                .name("<gold>Kill Limit: " + game.modeConfig().killLimit())
                .lore(Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        inventoryItems.put(SLOT_START_HP, ItemBuilder.from(Material.GOLDEN_APPLE)
                .name("<gold>Start HP: " + (int) game.modeConfig().startHp())
                .lore(Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        boolean repick = game.modeConfig().onRespawn() == RespawnAction.OPEN_HERO_SELECT;
        inventoryItems.put(SLOT_HERO_REPICK, repick
                ? ItemBuilder.from(Material.ENDER_PEARL)
                    .name("<green>Hero Re-Pick on Respawn: Enabled")
                    .build()
                : ItemBuilder.from(Material.ENDER_EYE)
                    .name("<red>Hero Re-Pick on Respawn: Disabled")
                    .build());

        inventoryItems.put(SLOT_ULT_CARRYOVER, game.modeConfig().ultChargeCarryover()
                ? ItemBuilder.from(Material.NETHER_STAR)
                    .name("<green>Ult Charge Carryover: Enabled")
                    .lore(Component.text("Smash charge % carries across hero swaps", NamedTextColor.GRAY))
                    .build()
                : ItemBuilder.from(Material.COAL)
                    .name("<red>Ult Charge Carryover: Disabled")
                    .lore(Component.text("Each new hero starts its ult from zero", NamedTextColor.GRAY))
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
            case SLOT_KILL_LIMIT -> handleKillLimit(event.getClick());
            case SLOT_START_HP -> handleStartHp(event.getClick());
            case SLOT_HERO_REPICK -> {
                boolean repick = game.modeConfig().onRespawn() == RespawnAction.OPEN_HERO_SELECT;
                game.setModeConfig(game.modeConfig().withOnRespawn(
                        repick ? RespawnAction.NONE : RespawnAction.OPEN_HERO_SELECT));
                SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
                update();
            }
            case SLOT_ULT_CARRYOVER -> {
                game.setModeConfig(game.modeConfig()
                        .withUltChargeCarryover(!game.modeConfig().ultChargeCarryover()));
                SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
                update();
            }
            case SLOT_BACK -> GUIManager.open(player, partyGUI);
        }
    }

    private void handleKillLimit(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the kill limit (5-200):", 5, 200,
                    value -> {
                        game.setModeConfig(game.modeConfig().withKillLimit(value));
                        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        int newLimit = switch (game.modeConfig().killLimit()) {
            case 10 -> 15;
            case 15 -> 20;
            case 20 -> 25;
            case 25 -> 30;
            default -> 10;
        };
        game.setModeConfig(game.modeConfig().withKillLimit(newLimit));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    private void handleStartHp(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the starting HP (10-500):", 10, 500,
                    value -> {
                        game.setModeConfig(game.modeConfig().withStartHp(value));
                        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
                        GUIManager.open(player, this);
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        double newHp = ((int) game.modeConfig().startHp()) == 50 ? 100 : 50;
        game.setModeConfig(game.modeConfig().withStartHp(newHp));
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    @Override
    protected GUI getParentGUI() {
        return partyGUI;
    }
}
