package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.rules.GameModeConfig;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** Shared {@link GameModeConfig} row builders reused across mode settings GUIs. */
public final class ModeRows {
    private static final int[] STOCKS_CYCLE = {1, 2, 3};
    private static final List<String> HEROES_TARGET_CYCLE = List.of("all", "3", "5", "7");
    private static final double[] START_HP_CYCLE = {50, 100};

    private ModeRows() {}

    public static Target<GameModeConfig> target(Game game) {
        return new Target<>(game::modeConfig, game::updateModeConfig);
    }

    public static Row stocks(int slot, Target<GameModeConfig> target, String key) {
        return Row.intValue(slot, target,
                new Lens<>(GameModeConfig::stocks, GameModeConfig::withStocks),
                key, Material.PAPER, STOCKS_CYCLE, 1, 10);
    }

    public static Row heroesTarget(int slot, Target<GameModeConfig> target, String key, Material mat) {
        return Row.choiceIntOrAll(slot, target,
                new Lens<>(GameModeConfig::heroesToEliminate, GameModeConfig::withHeroesToEliminate),
                key, mat, HEROES_TARGET_CYCLE, 1, 50);
    }

    public static Row startHp(int slot, Target<GameModeConfig> target, String key) {
        return Row.doubleValue(slot, target,
                new Lens<>(GameModeConfig::startHp, GameModeConfig::withStartHp),
                key, Material.GOLDEN_APPLE, null, START_HP_CYCLE, 10, 500, false,
                v -> Map.of("value", String.valueOf((int) v.doubleValue())));
    }

    public static Row ultCarryover(int slot, Target<GameModeConfig> target, String key) {
        return Row.toggle(slot, target,
                new Lens<>(GameModeConfig::ultChargeCarryover, GameModeConfig::withUltChargeCarryover),
                key, Material.NETHER_STAR, Material.COAL);
    }
}
