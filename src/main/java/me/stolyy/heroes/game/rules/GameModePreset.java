package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.util.GameModeConfigManager;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Selectable gamemode presets. Each builds its {@link GameModeConfig} from {@code gamemodes.yml}
 * defaults at selection time; preset-specific setup such as Boss lives/HP and Evolution hero orders
 * lives alongside those features.
 */
public enum GameModePreset {
    DEFAULT("Default"),
    BOSS("Boss"),
    TDM("Team Deathmatch"),
    EVOLUTION("Evolution"),
    SERIES("Series"),
    NUZLOCKE("Nuzlocke"),
    IRONMAN("Ironman");

    private static final List<String> COMMAND_TOKENS = List.of(
            "tdm", "evolution", "series", "nuzlocke", "ironman", "boss");

    private final String displayName;

    GameModePreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Preset tokens accepted by commands ({@code /join}, {@code /party ready}). */
    public static List<String> commandTokens() {
        return COMMAND_TOKENS;
    }

    public static Optional<GameModePreset> fromToken(String input) {
        if (input == null) return Optional.empty();
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "tdm" -> Optional.of(TDM);
            case "evolution", "evo" -> Optional.of(EVOLUTION);
            case "series" -> Optional.of(SERIES);
            case "nuzlocke" -> Optional.of(NUZLOCKE);
            case "ironman" -> Optional.of(IRONMAN);
            case "boss" -> Optional.of(BOSS);
            default -> Optional.empty();
        };
    }

    /** Series presets run as a MatchSeries of rounds instead of a single game. */
    public boolean isSeries() {
        return this == SERIES || this == NUZLOCKE || this == IRONMAN;
    }

    public GameModeConfig buildConfig() {
        return switch (this) {
            case DEFAULT, BOSS -> GameModeConfig.CLASSIC;

            case TDM -> GameModeConfig.builder()
                    .winCondition(WinCondition.KILL_TARGET)
                    .deathOutcome(DeathOutcome.ALWAYS_RESPAWN)
                    .onRespawn(GameModeConfigManager.getTdmChangeHeroOnRespawn()
                            ? RespawnAction.OPEN_HERO_SELECT : RespawnAction.NONE)
                    .killLimit(GameModeConfigManager.getTdmKillLimit())
                    .startHp(GameModeConfigManager.getTdmStartHp())
                    .stocks(0)
                    .ultChargeCarryover(GameModeConfigManager.getTdmUltCarryover())
                    .build();

            case EVOLUTION -> GameModeConfig.builder()
                    .winCondition(WinCondition.HERO_EXHAUSTION)
                    .deathOutcome(DeathOutcome.ALWAYS_RESPAWN)
                    .onKill(OnKillAction.ADVANCE_HERO)
                    .killLimit(GameModeConfigManager.getEvolutionKillLimit())
                    .startHp(GameModeConfigManager.getEvolutionStartHp())
                    .stocks(0)
                    .sharedEvolutionOrder(GameModeConfigManager.getEvolutionSharedOrder())
                    .ultChargeCarryover(GameModeConfigManager.getEvolutionUltCarryover())
                    .evolutionTeamAdvance(GameModeConfigManager.getEvolutionTeamAdvance())
                    .evolutionWinRequiresAll(GameModeConfigManager.getEvolutionWinRequiresAll())
                    .build();

            case SERIES -> GameModeConfig.builder()
                    .stocks(0)
                    .bestOf(GameModeConfigManager.getSeriesBestOf())
                    .repickBetweenRounds(GameModeConfigManager.getSeriesRepickBetweenRounds())
                    .build();

            case NUZLOCKE -> GameModeConfig.builder()
                    .stocks(GameModeConfigManager.getNuzlockeStocksPerGame())
                    .hpCarryover(GameModeConfigManager.getNuzlockeHpCarryover())
                    .lockIn(GameModeConfigManager.getNuzlockeLockIn())
                    .heroesToEliminate(GameModeConfigManager.getNuzlockeHeroesToEliminate())
                    .seriesTeamScope(GameModeConfigManager.getNuzlockeTeamScope())
                    .build();

            case IRONMAN -> GameModeConfig.builder()
                    .stocks(GameModeConfigManager.getIronmanStocksPerGame())
                    .heroesToEliminate(GameModeConfigManager.getIronmanHeroesToClear())
                    .seriesTeamScope(GameModeConfigManager.getIronmanTeamScope())
                    .build();
        };
    }
}
