package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.util.GameModeConfigManager;

/**
 * The selectable gamemode presets. Each builds its {@link GameModeConfig} from the
 * gamemodes.yml defaults at selection time (B13.7 GUI calls Game.setGameModePreset).
 * Preset-specific setup beyond the config — boss lives math, Evolution hero orders —
 * lives with each preset's B13.x task, not here.
 */
public enum GameModePreset {
    DEFAULT("Default"),
    BOSS("Boss"),
    TDM("Team Deathmatch"),
    EVOLUTION("Evolution"),
    SERIES("Series"),
    NUZLOCKE("Nuzlocke"),
    IRONMAN("Ironman");

    private final String displayName;

    GameModePreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Series presets run as a MatchSeries of rounds instead of a single game. */
    public boolean isSeries() {
        return this == SERIES || this == NUZLOCKE || this == IRONMAN;
    }

    public GameModeConfig buildConfig() {
        return switch (this) {
            case DEFAULT -> GameModeConfig.CLASSIC;

            // Boss numbers (boss lives = enemies * lives-per-enemy, non-boss lives/HP) are
            // per-player state applied by applyBossSetup (B13.2), not config-record fields.
            case BOSS -> new GameModeConfig(
                    WinCondition.LAST_TEAM_STANDING, DeathOutcome.LIVES_BASED,
                    OnKillAction.NONE, RespawnAction.NONE,
                    0, 0, 3,
                    GameModeConfigManager.getBossUltimatesEnabled(),
                    false, false, "all", 3, true, false, false, false, false, false);

            case TDM -> new GameModeConfig(
                    WinCondition.KILL_TARGET, DeathOutcome.ALWAYS_RESPAWN,
                    OnKillAction.NONE,
                    GameModeConfigManager.getTdmChangeHeroOnRespawn()
                            ? RespawnAction.OPEN_HERO_SELECT : RespawnAction.NONE,
                    GameModeConfigManager.getTdmKillLimit(),
                    GameModeConfigManager.getTdmStartHp(),
                    0, true, false, false, "all", 3, true, false,
                    GameModeConfigManager.getTdmUltCarryover(), false, false, false);

            case EVOLUTION -> new GameModeConfig(
                    WinCondition.HERO_EXHAUSTION, DeathOutcome.ALWAYS_RESPAWN,
                    OnKillAction.ADVANCE_HERO, RespawnAction.NONE,
                    GameModeConfigManager.getEvolutionKillLimit(),
                    GameModeConfigManager.getEvolutionStartHp(),
                    0, true, false, false, "all", 3, true,
                    GameModeConfigManager.getEvolutionSharedOrder(),
                    GameModeConfigManager.getEvolutionUltCarryover(),
                    GameModeConfigManager.getEvolutionTeamAdvance(),
                    GameModeConfigManager.getEvolutionWinRequiresAll(), false);

            // Plain best-of-X: classic rules per round, no hero elimination; stocks 0 keeps
            // each player's own lives setting.
            case SERIES -> new GameModeConfig(
                    WinCondition.LAST_TEAM_STANDING, DeathOutcome.LIVES_BASED,
                    OnKillAction.NONE, RespawnAction.NONE,
                    0, 0, 0, true, false, false, "all",
                    GameModeConfigManager.getSeriesBestOf(),
                    GameModeConfigManager.getSeriesRepickBetweenRounds(), false, false, false, false, false);

            case NUZLOCKE -> new GameModeConfig(
                    WinCondition.LAST_TEAM_STANDING, DeathOutcome.LIVES_BASED,
                    OnKillAction.NONE, RespawnAction.NONE,
                    0, 0,
                    GameModeConfigManager.getNuzlockeStocksPerGame(),
                    true,
                    GameModeConfigManager.getNuzlockeHpCarryover(),
                    GameModeConfigManager.getNuzlockeLockIn(),
                    GameModeConfigManager.getNuzlockeHeroesToEliminate(), 3, true, false, false, false, false,
                    GameModeConfigManager.getNuzlockeTeamScope());

            case IRONMAN -> new GameModeConfig(
                    WinCondition.LAST_TEAM_STANDING, DeathOutcome.LIVES_BASED,
                    OnKillAction.NONE, RespawnAction.NONE,
                    0, 0,
                    GameModeConfigManager.getIronmanStocksPerGame(),
                    true, false, false,
                    GameModeConfigManager.getIronmanHeroesToClear(), 3, true, false, false, false, false,
                    GameModeConfigManager.getIronmanTeamScope());
        };
    }
}
