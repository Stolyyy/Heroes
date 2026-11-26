package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/** Plain best-of-X series settings: round target and hero re-pick between rounds. */
public class SeriesSettingsGUI extends SettingsGUI {
    private static final int SLOT_BEST_OF = 12;
    private static final int SLOT_REPICK = 14;
    private static final int SLOT_BACK = 22;
    private static final int[] BEST_OF_CYCLE = {3, 5, 7};

    public SeriesSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(game, player, 27, GuiText.raw("gui.settings.series.title"), partyGUI, SLOT_BACK, false);
        Target<GameModeConfig> target = ModeRows.target(game);

        addRow(Row.intValue(SLOT_BEST_OF, target,
                new Lens<>(GameModeConfig::bestOf, GameModeConfig::withBestOf),
                "gui.settings.series.best-of", Material.CLOCK, BEST_OF_CYCLE, 1, 15,
                v -> Map.of("value", String.valueOf(v), "wins", String.valueOf(v / 2 + 1))));
        addRow(Row.toggle(SLOT_REPICK, target,
                new Lens<>(GameModeConfig::repickBetweenRounds, GameModeConfig::withRepickBetweenRounds),
                "gui.settings.series.repick", Material.ENDER_PEARL, Material.ENDER_EYE));
    }
}
