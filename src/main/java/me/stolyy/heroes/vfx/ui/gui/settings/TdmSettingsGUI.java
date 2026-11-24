package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.game.rules.RespawnAction;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Team Deathmatch settings: kill limit, start HP, respawn re-pick, and ult carryover. */
public class TdmSettingsGUI extends SettingsGUI {
    private static final int SLOT_KILL_LIMIT = 11;
    private static final int SLOT_START_HP = 13;
    private static final int SLOT_HERO_REPICK = 15;
    private static final int SLOT_ULT_CARRYOVER = 17;
    private static final int SLOT_BACK = 22;
    private static final int[] KILL_LIMIT_CYCLE = {10, 15, 20, 25, 30};

    public TdmSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(game, player, 27, GuiText.raw("gui.settings.tdm.title"), partyGUI, SLOT_BACK, false);
        Target<GameModeConfig> target = ModeRows.target(game);

        addRow(Row.intValue(SLOT_KILL_LIMIT, target,
                new Lens<>(GameModeConfig::killLimit, GameModeConfig::withKillLimit),
                "gui.settings.tdm.kill-limit", Material.IRON_SWORD, KILL_LIMIT_CYCLE, 5, 200));
        addRow(ModeRows.startHp(SLOT_START_HP, target, "gui.settings.tdm.start-hp"));
        addRow(Row.toggle(SLOT_HERO_REPICK, target,
                new Lens<>(cfg -> cfg.onRespawn() == RespawnAction.OPEN_HERO_SELECT,
                        (cfg, on) -> cfg.withOnRespawn(on ? RespawnAction.OPEN_HERO_SELECT : RespawnAction.NONE)),
                "gui.settings.tdm.hero-repick", Material.ENDER_PEARL, Material.ENDER_EYE));
        addRow(ModeRows.ultCarryover(SLOT_ULT_CARRYOVER, target, "gui.settings.tdm.ult-carryover"));
    }
}
