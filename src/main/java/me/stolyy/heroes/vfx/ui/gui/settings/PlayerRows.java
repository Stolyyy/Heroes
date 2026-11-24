package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.game.settings.PlayerSettings;
import org.bukkit.Material;

import java.util.Map;

/** Shared {@link PlayerSettings} rows used by both {@link GameSettingsGUI} and {@link PlayerSettingsGUI}. */
public final class PlayerRows {
    public static final int SLOT_LIVES = 9;
    public static final int SLOT_HEALTH = 11;
    public static final int SLOT_ULTIMATES = 13;
    public static final int SLOT_FRIENDLY_FIRE = 15;
    public static final int SLOT_RANDOM_HEROES = 17;

    private static final int[] LIVES_CYCLE = {1, 2, 3, 4};
    private static final double[] HEALTH_CYCLE = {50, 100, 150, 200, 300};

    private PlayerRows() {}

    public static void addAll(SettingsGUI gui, Game game, Target<PlayerSettings> target) {
        gui.addRow(Row.intValue(
                SLOT_LIVES,
                target,
                Lens.mutable(PlayerSettings::lives, PlayerSettings::setLives),
                "gui.settings.player.lives",
                Material.PAPER,
                new ModelKey("gui", "settings", "lives"),
                LIVES_CYCLE,
                3,
                1, 20,
                v -> Map.of("value", String.valueOf(v)),
                () -> game == null || game.modeConfig().deathOutcome().consumesLife()));

        gui.addRow(Row.doubleValue(
                SLOT_HEALTH,
                target,
                Lens.mutable(PlayerSettings::health, PlayerSettings::setHealth),
                "gui.settings.player.health",
                Material.GOLDEN_APPLE,
                new ModelKey("gui", "settings", "health"),
                HEALTH_CYCLE,
                0, 1000,
                false,
                v -> Map.of("value", String.valueOf((int) v.doubleValue()))));

        gui.addRow(Row.toggle(
                SLOT_ULTIMATES,
                target,
                Lens.mutable(PlayerSettings::ultimatesEnabled, PlayerSettings::setUltimatesEnabled),
                "gui.settings.player.ultimates",
                Material.NETHER_STAR, new ModelKey("gui", "settings", "ultimate-on"),
                Material.COAL, new ModelKey("gui", "settings", "ultimate-off")));

        gui.addRow(Row.toggle(
                SLOT_FRIENDLY_FIRE,
                target,
                Lens.mutable(PlayerSettings::friendlyFire, PlayerSettings::setFriendlyFire),
                "gui.settings.player.friendly-fire",
                Material.GOLDEN_SWORD, new ModelKey("gui", "settings", "friendly-on"),
                Material.STONE_SWORD, new ModelKey("gui", "settings", "friendly-off")));

        gui.addRow(Row.toggle(
                SLOT_RANDOM_HEROES,
                target,
                Lens.mutable(PlayerSettings::randomizeHeroes, PlayerSettings::setRandomizeHeroes),
                "gui.settings.player.random-heroes",
                Material.PAPER, new ModelKey("gui", "settings", "random-on"),
                Material.PAPER, new ModelKey("gui", "settings", "random-off"),
                () -> game == null || game.gameModePreset() != GameModePreset.EVOLUTION));
    }
}
