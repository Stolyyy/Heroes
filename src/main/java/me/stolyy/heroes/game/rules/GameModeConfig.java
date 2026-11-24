package me.stolyy.heroes.game.rules;

/**
 * A gamemode's rules as pure data (composition over subclassing). Built by
 * {@link GameModePreset} from {@code gamemodes.yml} defaults and installed on a {@link me.stolyy.heroes.game.Game}
 * via {@code updateModeConfig} / {@code setGameModePreset} before {@code start()}.
 *
 * <p>{@code killLimit <= 0} disables the kill target; {@code startHp <= 0} keeps hero base HP;
 * {@code stocks == 0} in series modes preserves each player's own lives setting; {@code heroesToEliminate}
 * is a numeric count or {@code "all"}; {@code bestOf} is the plain-series target round cap.
 */
public record GameModeConfig(
        WinCondition winCondition,
        DeathOutcome deathOutcome,
        OnKillAction onKill,
        RespawnAction onRespawn,
        int killLimit,
        double startHp,
        int stocks,
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

    /** Classic rules: last team standing, lives-based elimination, no kill/respawn hooks. */
    public static final GameModeConfig CLASSIC = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .winCondition(winCondition)
                .deathOutcome(deathOutcome)
                .onKill(onKill)
                .onRespawn(onRespawn)
                .killLimit(killLimit)
                .startHp(startHp)
                .stocks(stocks)
                .hpCarryover(hpCarryover)
                .lockIn(lockIn)
                .heroesToEliminate(heroesToEliminate)
                .bestOf(bestOf)
                .repickBetweenRounds(repickBetweenRounds)
                .sharedEvolutionOrder(sharedEvolutionOrder)
                .ultChargeCarryover(ultChargeCarryover)
                .evolutionTeamAdvance(evolutionTeamAdvance)
                .evolutionWinRequiresAll(evolutionWinRequiresAll)
                .seriesTeamScope(seriesTeamScope);
    }

    public static final class Builder {
        private WinCondition winCondition = WinCondition.LAST_TEAM_STANDING;
        private DeathOutcome deathOutcome = DeathOutcome.LIVES_BASED;
        private OnKillAction onKill = OnKillAction.NONE;
        private RespawnAction onRespawn = RespawnAction.NONE;
        private int killLimit = 0;
        private double startHp = 0;
        private int stocks = 3;
        private boolean hpCarryover = false;
        private boolean lockIn = false;
        private String heroesToEliminate = "all";
        private int bestOf = 3;
        private boolean repickBetweenRounds = true;
        private boolean sharedEvolutionOrder = false;
        private boolean ultChargeCarryover = false;
        private boolean evolutionTeamAdvance = false;
        private boolean evolutionWinRequiresAll = false;
        private boolean seriesTeamScope = false;

        private Builder() {}

        public Builder winCondition(WinCondition winCondition) { this.winCondition = winCondition; return this; }
        public Builder deathOutcome(DeathOutcome deathOutcome) { this.deathOutcome = deathOutcome; return this; }
        public Builder onKill(OnKillAction onKill) { this.onKill = onKill; return this; }
        public Builder onRespawn(RespawnAction onRespawn) { this.onRespawn = onRespawn; return this; }
        public Builder killLimit(int killLimit) { this.killLimit = killLimit; return this; }
        public Builder startHp(double startHp) { this.startHp = startHp; return this; }
        public Builder stocks(int stocks) { this.stocks = stocks; return this; }
        public Builder hpCarryover(boolean hpCarryover) { this.hpCarryover = hpCarryover; return this; }
        public Builder lockIn(boolean lockIn) { this.lockIn = lockIn; return this; }
        public Builder heroesToEliminate(String heroesToEliminate) { this.heroesToEliminate = heroesToEliminate; return this; }
        public Builder bestOf(int bestOf) { this.bestOf = bestOf; return this; }
        public Builder repickBetweenRounds(boolean repickBetweenRounds) { this.repickBetweenRounds = repickBetweenRounds; return this; }
        public Builder sharedEvolutionOrder(boolean sharedEvolutionOrder) { this.sharedEvolutionOrder = sharedEvolutionOrder; return this; }
        public Builder ultChargeCarryover(boolean ultChargeCarryover) { this.ultChargeCarryover = ultChargeCarryover; return this; }
        public Builder evolutionTeamAdvance(boolean evolutionTeamAdvance) { this.evolutionTeamAdvance = evolutionTeamAdvance; return this; }
        public Builder evolutionWinRequiresAll(boolean evolutionWinRequiresAll) { this.evolutionWinRequiresAll = evolutionWinRequiresAll; return this; }
        public Builder seriesTeamScope(boolean seriesTeamScope) { this.seriesTeamScope = seriesTeamScope; return this; }

        public GameModeConfig build() {
            return new GameModeConfig(
                    winCondition, deathOutcome, onKill, onRespawn,
                    killLimit, startHp, stocks, hpCarryover, lockIn, heroesToEliminate,
                    bestOf, repickBetweenRounds, sharedEvolutionOrder, ultChargeCarryover,
                    evolutionTeamAdvance, evolutionWinRequiresAll, seriesTeamScope);
        }
    }

    public GameModeConfig withKillLimit(int killLimit) { return toBuilder().killLimit(killLimit).build(); }
    public GameModeConfig withStartHp(double startHp) { return toBuilder().startHp(startHp).build(); }
    public GameModeConfig withOnRespawn(RespawnAction onRespawn) { return toBuilder().onRespawn(onRespawn).build(); }
    public GameModeConfig withStocks(int stocks) { return toBuilder().stocks(stocks).build(); }
    public GameModeConfig withHpCarryover(boolean hpCarryover) { return toBuilder().hpCarryover(hpCarryover).build(); }
    public GameModeConfig withLockIn(boolean lockIn) { return toBuilder().lockIn(lockIn).build(); }
    public GameModeConfig withHeroesToEliminate(String heroesToEliminate) { return toBuilder().heroesToEliminate(heroesToEliminate).build(); }
    public GameModeConfig withBestOf(int bestOf) { return toBuilder().bestOf(bestOf).build(); }
    public GameModeConfig withRepickBetweenRounds(boolean repickBetweenRounds) { return toBuilder().repickBetweenRounds(repickBetweenRounds).build(); }
    public GameModeConfig withSharedEvolutionOrder(boolean sharedEvolutionOrder) { return toBuilder().sharedEvolutionOrder(sharedEvolutionOrder).build(); }
    public GameModeConfig withUltChargeCarryover(boolean ultChargeCarryover) { return toBuilder().ultChargeCarryover(ultChargeCarryover).build(); }
    public GameModeConfig withEvolutionTeamAdvance(boolean evolutionTeamAdvance) { return toBuilder().evolutionTeamAdvance(evolutionTeamAdvance).build(); }
    public GameModeConfig withEvolutionWinRequiresAll(boolean evolutionWinRequiresAll) { return toBuilder().evolutionWinRequiresAll(evolutionWinRequiresAll).build(); }
    public GameModeConfig withSeriesTeamScope(boolean seriesTeamScope) { return toBuilder().seriesTeamScope(seriesTeamScope).build(); }
}
