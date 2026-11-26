package me.stolyy.heroes.progression;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-mode hero record: wins, losses, deaths, and per-ability stats. Mode-agnostic level and XP
 * live on {@link HeroLevel}.
 */
public class HeroProgress {
    private int wins;
    private int losses;
    private int deaths;
    private final Map<String, AbilityStats> abilityStats;

    public HeroProgress() {
        this.abilityStats = new HashMap<>();
    }

    public void addWin() { wins++; }
    public void addLoss() { losses++; }
    public void addDeath() { deaths++; }

    public AbilityStats getAbilityStats(String abilityName) {
        return abilityStats.computeIfAbsent(abilityName, k -> new AbilityStats());
    }

    public AbilityStats totals() {
        return abilityStats.values().stream()
            .reduce(new AbilityStats(), AbilityStats::combine);
    }

    public int wins() { return wins; }
    public int losses() { return losses; }
    public int deaths() { return deaths; }
    public Map<String, AbilityStats> abilityStats() { return abilityStats; }

    public void setWins(int wins) { this.wins = wins; }
    public void setLosses(int losses) { this.losses = losses; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
}
