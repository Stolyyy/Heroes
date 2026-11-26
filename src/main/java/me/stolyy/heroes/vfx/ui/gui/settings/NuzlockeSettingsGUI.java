package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Nuzlocke settings: stocks per round, HP carry-over, lock-in, team scope, and heroes to eliminate. */
public class NuzlockeSettingsGUI extends SettingsGUI {
    private static final int SLOT_TEAM_SCOPE = 4;
    private static final int SLOT_STOCKS = 10;
    private static final int SLOT_HP_CARRYOVER = 12;
    private static final int SLOT_LOCK_IN = 14;
    private static final int SLOT_HEROES = 16;
    private static final int SLOT_BACK = 22;

    public NuzlockeSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(game, player, 27, GuiText.raw("gui.settings.nuzlocke.title"), partyGUI, SLOT_BACK, false);
        Target<GameModeConfig> target = ModeRows.target(game);

        addRow(Row.toggle(SLOT_TEAM_SCOPE, target,
                new Lens<>(GameModeConfig::seriesTeamScope, GameModeConfig::withSeriesTeamScope),
                "gui.settings.nuzlocke.team-scope", Material.LEAD, Material.ENDER_PEARL));
        addRow(ModeRows.stocks(SLOT_STOCKS, target, "gui.settings.nuzlocke.stocks"));
        addRow(Row.toggle(SLOT_HP_CARRYOVER, target,
                new Lens<>(GameModeConfig::hpCarryover, GameModeConfig::withHpCarryover),
                "gui.settings.nuzlocke.hp-carryover", Material.GOLDEN_APPLE, Material.APPLE));
        addRow(Row.toggle(SLOT_LOCK_IN, target,
                new Lens<>(GameModeConfig::lockIn, GameModeConfig::withLockIn),
                "gui.settings.nuzlocke.lock-in", Material.IRON_DOOR, Material.OAK_DOOR));
        addRow(ModeRows.heroesTarget(SLOT_HEROES, target, "gui.settings.nuzlocke.heroes", Material.SKELETON_SKULL));
    }
}
