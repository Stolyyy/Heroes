package me.stolyy.heroes.game.series;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-(player,hero) state within one MatchSeries: which heroes are spent/eliminated and
 * what HP each surviving hero carries between rounds.
 *
 * <p><b>Sentinel:</b> {@code -1.0} = dead/spent. 0.0 is a legal ALIVE value (the 0-HP
 * one-hit-buffer rule,), so deadness cannot be encoded as 0. Absent key = untouched
 * hero at full HP.
 *
 * <p>Ephemeral per-series — carry-over is between ROUNDS of one series. Cross-session
 * persistence is a possible future extension, deliberately not built.
 */
public class SeriesPlayerState {
    public static final double DEAD = -1.0;

    private final UUID playerId;
    private final Map<String, Double> heroHp = new HashMap<>();

    public SeriesPlayerState(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public void markDead(String heroName) {
        heroHp.put(heroName, DEAD);
    }

    public boolean isDead(String heroName) {
        Double hp = heroHp.get(heroName);
        return hp != null && hp == DEAD;
    }

    public void setHeroHp(String heroName, double hp) {
        heroHp.put(heroName, hp);
    }

    /** Raw stored value: null = untouched, {@link #DEAD} = dead/spent, else carry-over HP. */
    public Double storedHp(String heroName) {
        return heroHp.get(heroName);
    }

    public Set<String> deadHeroes() {
        return heroHp.entrySet().stream()
                .filter(e -> e.getValue() == DEAD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
