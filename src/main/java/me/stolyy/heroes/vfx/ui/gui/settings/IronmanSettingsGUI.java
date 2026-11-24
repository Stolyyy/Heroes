package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Ironman settings: stocks per round, team win scope, and heroes to clear. */
public class IronmanSettingsGUI extends SettingsGUI {
    private static final int SLOT_STOCKS = 11;
    private static final int SLOT_TEAM_SCOPE = 13;
    private static final int SLOT_HEROES = 15;
    private static final int SLOT_BACK = 22;

    public IronmanSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(game, player, 27, GuiText.raw("gui.settings.ironman.title"), partyGUI, SLOT_BACK, false);
        Target<GameModeConfig> target = ModeRows.target(game);

        addRow(ModeRows.stocks(SLOT_STOCKS, target, "gui.settings.ironman.stocks"));
        addRow(Row.toggle(SLOT_TEAM_SCOPE, target,
                new Lens<>(GameModeConfig::seriesTeamScope, GameModeConfig::withSeriesTeamScope),
                "gui.settings.ironman.team-scope", Material.TOTEM_OF_UNDYING, Material.GOLD_NUGGET));
        addRow(ModeRows.heroesTarget(SLOT_HEROES, target, "gui.settings.ironman.heroes", Material.GOLDEN_HELMET));
    }
}
