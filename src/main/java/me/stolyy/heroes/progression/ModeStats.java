package me.stolyy.heroes.progression;

import java.util.HashMap;
import java.util.Map;

public class ModeStats {
    private final Map<String, HeroProgress> heroStats;

    public ModeStats() {
        this.heroStats = new HashMap<>();
    }

    public HeroProgress getHeroStats(String heroName) {
        return heroStats.computeIfAbsent(heroName, k -> new HeroProgress());
    }

    public int totalWins() {
        return heroStats.values().stream().mapToInt(HeroProgress::wins).sum();
    }

    public int totalLosses() {
        return heroStats.values().stream().mapToInt(HeroProgress::losses).sum();
    }

    public int totalDeaths() {
        return heroStats.values().stream().mapToInt(HeroProgress::deaths).sum();
    }

    public AbilityStats totalAbilityStats() {
        return heroStats.values().stream()
            .map(HeroProgress::totals)
            .reduce(new AbilityStats(), AbilityStats::combine);
    }

    public int totalKills() { return totalAbilityStats().kills(); }
    public double totalDamage() { return totalAbilityStats().damage(); }
    public double totalVoidDamage() { return totalAbilityStats().voidDamage(); }

    public Map<String, HeroProgress> heroStats() { return heroStats; }
}
