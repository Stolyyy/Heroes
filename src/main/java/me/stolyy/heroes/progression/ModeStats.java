package me.stolyy.heroes.progression;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ModeStats {
    private final Map<String, HeroProgress> heroStats;

    public ModeStats() {
        this.heroStats = new HashMap<>();
    }

    public HeroProgress getHeroStats(String heroName) {
        String key = heroName.toLowerCase(Locale.ROOT);
        HeroProgress exact = heroStats.get(key);
        if (exact != null) return exact;
        HeroProgress legacy = findLegacyHeroStats(heroName);
        return legacy != null ? legacy : heroStats.computeIfAbsent(key, k -> new HeroProgress());
    }

    public @Nullable HeroProgress findHeroStats(String heroName) {
        HeroProgress exact = heroStats.get(heroName.toLowerCase(Locale.ROOT));
        return exact != null ? exact : findLegacyHeroStats(heroName);
    }

    private @Nullable HeroProgress findLegacyHeroStats(String heroName) {
        for (Map.Entry<String, HeroProgress> entry : heroStats.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(heroName)) return entry.getValue();
        }
        return null;
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
