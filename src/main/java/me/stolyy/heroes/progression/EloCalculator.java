package me.stolyy.heroes.progression;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.util.GameConfigManager;

import java.util.Collections;
import java.util.List;

/**
 * Team-average Elo with a match-wide K that shrinks as either team gets internally lopsided.
 *
 * <p>The expectation is always computed from TEAM AVERAGES, never from an individual against the
 * opposing team's average. Rating individuals against the enemy average is not zero-sum (it injects
 * points on every lopsided match, in both directions) and it is directly farmable: a 400-rated player
 * duoing with a 1600-rated friend against two 1000s would gain +31 per win and lose only -1 per loss.
 *
 * <p>What the gap damping addresses instead is VOLATILITY. When one side's average is a bad estimate
 * of that side's real strength, the outcome is weak evidence about everybody in the match — you learn
 * little about the low-rated player (carried?), little about the high-rated one (dragged down?), and
 * little about the opponents, who faced a team whose mean lies. A smaller K is the statistically
 * honest response to a low-information result, and it happens to be what "this is harsh on the player
 * with the least control" is actually asking for.
 *
 * <p>The damping is SYMMETRIC by construction — one K for the whole match, so a lopsided-team win
 * pays exactly as little as a lopsided-team loss costs. That symmetry is the anti-exploit property.
 * An asymmetric variant (full gain, damped loss) would hand a low-rated player a money printer: queue
 * with a strong friend, pocket the wins at full value, pay a discount on the losses, climb forever
 * without improving. Because both directions shrink together, the stacked duo gains LESS per win too,
 * so there is nothing to farm — the worst a permanently-lopsided duo can do is freeze their own
 * ratings in place, which is stalling, not farming.
 */
public class EloCalculator {
    private final int kFactor;
    private final double gapCap;
    private final double maxGapReduction;

    public EloCalculator(int kFactor, double gapCap, double maxGapReduction) {
        this.kFactor = kFactor;
        this.gapCap = gapCap;
        this.maxGapReduction = maxGapReduction;
    }

    public static EloCalculator forMode(GameMode mode) {
        return new EloCalculator(
            GameConfigManager.getKFactor(mode),
            GameConfigManager.getEloGapCap(),
            GameConfigManager.getEloMaxGapReduction()
        );
    }

    /** Single rating per side, i.e. the 1v1 shape: no teammate, so no gap and no damping. */
    public static EloResult calculate(int winnerElo, int loserElo) {
        return calculate(GameMode.ONE_V_ONE, List.of(winnerElo), List.of(loserElo));
    }

    /** Every original participant's rating, per side — the individual values are what the gap needs. */
    public static EloResult calculate(GameMode mode, List<Integer> winnerRatings, List<Integer> loserRatings) {
        return forMode(mode).calculateResult(winnerRatings, loserRatings);
    }

    public EloResult calculateResult(List<Integer> winnerRatings, List<Integer> loserRatings) {
        double winnerAvg = average(winnerRatings);
        double loserAvg = average(loserRatings);
        double expectedWinner = 1.0 / (1.0 + Math.pow(10, (loserAvg - winnerAvg) / 400.0));

        int gap = Math.max(spread(winnerRatings), spread(loserRatings));
        double k = kFactor * (1.0 - maxGapReduction * Math.min(1.0, gap / gapCap));

        int winnerChange = (int) Math.round(k * (1 - expectedWinner));
        // The loser's term is k * (0 - (1 - expectedWinner)), i.e. exactly the winner's negated.
        // Negating the rounded value instead of rounding twice keeps the match strictly zero-sum.
        return new EloResult(winnerChange, -winnerChange);
    }

    private static double average(List<Integer> ratings) {
        return ratings.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }

    private static int spread(List<Integer> ratings) {
        return Collections.max(ratings) - Collections.min(ratings);
    }

    public record EloResult(int winnerChange, int loserChange) {}
}
