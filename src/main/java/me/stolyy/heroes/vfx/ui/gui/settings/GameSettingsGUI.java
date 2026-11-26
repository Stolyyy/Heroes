package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.settings.GameSettings;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import me.stolyy.heroes.vfx.ui.prompt.PromptManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Custom match global settings: default player settings (fanned out live to all current
 * participants) plus crystals, kill walls, and match timer.
 */
public class GameSettingsGUI extends SettingsGUI {
    private static final int SLOT_CRYSTALS = 28;
    private static final int SLOT_WALLS = 32;
    private static final int SLOT_TIMER = 34;
    private static final int SLOT_CLOSE = 49;
    private static final int[] TIMER_CYCLE = {180, 300, 420, 600};

    public GameSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(game, player, 54, GuiText.raw("gui.settings.game.title"), partyGUI, SLOT_CLOSE, true);

        Target<PlayerSettings> playerTarget = new Target<>(
                game::defaultPlayerSettings,
                change -> game.applySettingsAll(change::apply));
        PlayerRows.addAll(this, game, playerTarget);

        Target<GameSettings> gameTarget = new Target<>(
                game::settings,
                change -> change.apply(game.settings()));

        addRow(Row.action(SLOT_CRYSTALS, this::renderCrystals, this::handleCrystals));
        addRow(Row.toggle(SLOT_WALLS, gameTarget,
                Lens.mutable(GameSettings::killWalls, (s, v) -> s.toggleKillWalls()),
                "gui.settings.game.walls",
                Material.RED_SANDSTONE_WALL, new ModelKey("gui", "settings", "walls-on"),
                Material.SANDSTONE_WALL, new ModelKey("gui", "settings", "walls-off")));
        addRow(Row.intValue(SLOT_TIMER, gameTarget,
                Lens.mutable(GameSettings::timer, GameSettings::setTimer),
                "gui.settings.game.timer",
                Material.CLOCK, new ModelKey("gui", "settings", "timer"),
                TIMER_CYCLE, 420, 60, 3600,
                v -> Map.of("value", formatTime(v)),
                () -> true));
    }

    private ItemStack renderCrystals() {
        boolean on = game.settings().smashCrystals();
        return ItemBuilder.from(on ? Material.END_CRYSTAL : Material.GLASS)
                .name(on ? GuiText.component("gui.settings.game.crystals.on", "interval", game.settings().crystalInterval())
                         : GuiText.component("gui.settings.game.crystals.off"))
                .lore(GuiText.lore("gui.settings.game.crystals.lore"))
                .modelData("gui", "settings", on ? "crystals-on" : "crystals-off")
                .build();
    }

    private void handleCrystals(SettingsGUI host, ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(host.player(),
                    GuiText.raw("gui.settings.game.crystals.prompt", "min", 10, "max", 600),
                    10, 600,
                    value -> {
                        game.settings().setCrystalInterval(value);
                        host.playPromptConfirm();
                        host.reopen();
                    },
                    host::reopen);
            return;
        }
        game.settings().setSmashCrystals(!game.settings().smashCrystals());
        host.playConfirm();
        host.refresh();
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
