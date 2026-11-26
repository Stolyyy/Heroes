package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Evolution settings: kill limit, start HP, team advance, win condition, shared order, and ult carryover. */
public class EvolutionSettingsGUI extends SettingsGUI {
    private static final int SLOT_TEAM_ADVANCE = 10;
    private static final int SLOT_KILL_LIMIT = 11;
    private static final int SLOT_WIN_ALL = 12;
    private static final int SLOT_START_HP = 13;
    private static final int SLOT_SHARED_ORDER = 15;
    private static final int SLOT_ULT_CARRYOVER = 17;
    private static final int SLOT_BACK = 22;
    private static final int[] KILL_LIMIT_CYCLE = {0, 10, 15, 20, 25, 30};

    public EvolutionSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(game, player, 27, GuiText.raw("gui.settings.evolution.title"), partyGUI, SLOT_BACK, false);
        Target<GameModeConfig> target = ModeRows.target(game);

        addRow(Row.toggle(SLOT_TEAM_ADVANCE, target,
                new Lens<>(GameModeConfig::evolutionTeamAdvance, GameModeConfig::withEvolutionTeamAdvance),
                "gui.settings.evolution.team-advance", Material.BELL, Material.ARROW));
        addRow(Row.intValue(SLOT_KILL_LIMIT, target,
                new Lens<>(GameModeConfig::killLimit, GameModeConfig::withKillLimit),
                "gui.settings.evolution.kill-limit", Material.IRON_SWORD, KILL_LIMIT_CYCLE, 0, 200));
        addRow(Row.toggle(SLOT_WIN_ALL, target,
                new Lens<>(GameModeConfig::evolutionWinRequiresAll, GameModeConfig::withEvolutionWinRequiresAll),
                "gui.settings.evolution.win-all", Material.TOTEM_OF_UNDYING, Material.GOLD_NUGGET));
        addRow(ModeRows.startHp(SLOT_START_HP, target, "gui.settings.evolution.start-hp"));
        addRow(Row.toggle(SLOT_SHARED_ORDER, target,
                new Lens<>(GameModeConfig::sharedEvolutionOrder, GameModeConfig::withSharedEvolutionOrder),
                "gui.settings.evolution.shared-order", Material.LEAD, Material.ENDER_PEARL));
        addRow(ModeRows.ultCarryover(SLOT_ULT_CARRYOVER, target, "gui.settings.evolution.ult-carryover"));
    }
}
