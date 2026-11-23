package me.stolyy.heroes.game.rules;

/**
 * A gamemode's rules as pure data (A4 design: composition, not subclassing).
 * A preset = behavior selections + numbers, built by {@link GameModePreset} from
 * gamemodes.yml defaults. Installed on a Game via setModeConfig / setGameModePreset
 * BEFORE start().
 *
 * <p>Field semantics: {@code killLimit} &lt;= 0 means no kill target; {@code startHp}
 * 0 means no override (hero base HP); {@code stocks} is the per-round stock count for
 * series modes (0 = keep each player's own lives setting; classic lives come from
 * PlayerSettings, not from here); {@code heroesToEliminate} is a count or 'all'
 * (series modes, read by A6); {@code bestOf} is the plain-SERIES round target (first
 * to bestOf/2+1 round wins takes the series); {@code repickBetweenRounds} opens a free
 * hero pick between plain-SERIES rounds; {@code sharedEvolutionOrder} gives every
 * Evolution participant the same shuffled hero order; {@code ultChargeCarryover} carries
 * the smash cooldown's charge percentage across mid-game hero swaps (TDM/Evolution).
 */
public record GameModeConfig(
        WinCondition winCondition,
        DeathOutcome deathOutcome,
        OnKillAction onKill,
        RespawnAction onRespawn,
        int killLimit,
        double startHp,
        int stocks,
        boolean ultsEnabled,
        boolean hpCarryover,
        boolean lockIn,
        String heroesToEliminate,
        int bestOf,
        boolean repickBetweenRounds,
        boolean sharedEvolutionOrder,
        boolean ultChargeCarryover,
        boolean evolutionTeamAdvance,
        boolean evolutionWinRequiresAll,
        boolean seriesTeamScope) {

    /** Today's rules: last team standing, lives-based elimination, no kill/respawn hooks. */
    public static final GameModeConfig CLASSIC = new GameModeConfig(
            WinCondition.LAST_TEAM_STANDING,
            DeathOutcome.LIVES_BASED,
            OnKillAction.NONE,
            RespawnAction.NONE,
            0, 0, 3, true, false, false, "all", 3, true, false, false, false, false, false);

    // Withers for the mode-settings GUIs (records have no built-in copy-with).
    public GameModeConfig withKillLimit(int killLimit) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withStartHp(double startHp) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withOnRespawn(RespawnAction onRespawn) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withStocks(int stocks) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withHpCarryover(boolean hpCarryover) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withLockIn(boolean lockIn) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withHeroesToEliminate(String heroesToEliminate) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withBestOf(int bestOf) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withRepickBetweenRounds(boolean repickBetweenRounds) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withSharedEvolutionOrder(boolean sharedEvolutionOrder) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withUltChargeCarryover(boolean ultChargeCarryover) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn,
                killLimit, startHp, stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate,
                bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }
    public GameModeConfig withEvolutionTeamAdvance(boolean evolutionTeamAdvance) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn, killLimit, startHp,
                stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate, bestOf, repickBetweenRounds,
                sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withEvolutionWinRequiresAll(boolean evolutionWinRequiresAll) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn, killLimit, startHp,
                stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate, bestOf, repickBetweenRounds,
                sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }

    public GameModeConfig withSeriesTeamScope(boolean seriesTeamScope) {
        return new GameModeConfig(winCondition, deathOutcome, onKill, onRespawn, killLimit, startHp,
                stocks, ultsEnabled, hpCarryover, lockIn, heroesToEliminate, bestOf, repickBetweenRounds,
                sharedEvolutionOrder, ultChargeCarryover, evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
    }
}
