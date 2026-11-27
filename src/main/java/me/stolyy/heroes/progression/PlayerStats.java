package me.stolyy.heroes.progression;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.util.GameConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerStats {
    public record HeroSummary(int wins, int losses, int deaths, int kills, double damage,
                              double voidDamage, Map<String, AbilityStats> abilities) {}

    public record AbilityLookup(@Nullable String matchedKey, AbilityStats stats, List<String> available) {}

    private final UUID playerId;
    private String lastKnownName;
    private long lastSeen;
    private String selectedHero;

    private final Map<GameMode, Integer> elo;
    private final Map<GameMode, ModeStats> modeStats;
    private final Set<String> equippedCharms;

    // Unlock economy + per-hero skin choice (keyed by lowercase hero name).
    private int tokenBalance;
    private final Set<String> unlockedHeroes = new HashSet<>();
    private final Map<String, String> selectedSkins = new HashMap<>();

    // Global hero level/XP pool (mode-agnostic) — keyed by lowercase hero name.
    private final Map<String, HeroLevel> heroLevels = new HashMap<>();

    // Read by the async chat renderer, hence volatile.
    private volatile boolean seasonChampion;

    private GameMode currentMode;
    private String currentHero;

    public PlayerStats(UUID playerId) {
        this.playerId = playerId;
        this.elo = new EnumMap<>(GameMode.class);
        this.modeStats = new EnumMap<>(GameMode.class);
        this.equippedCharms = new HashSet<>();

        int startingElo = GameConfigManager.getStartingElo();
        elo.put(GameMode.ONE_V_ONE, startingElo);
        elo.put(GameMode.TWO_V_TWO, startingElo);

        // New-player seed; deserialization overwrites these for existing players.
        unlockedHeroes.addAll(ProgressionConfig.getStartingHeroes());
        tokenBalance = ProgressionConfig.getStartingTokens();
    }

    public ModeStats getModeStats(GameMode mode) {
        return modeStats.computeIfAbsent(mode, k -> new ModeStats());
    }

    public HeroProgress getHeroStats(GameMode mode, String heroName) {
        return getModeStats(mode).getHeroStats(heroName);
    }

    public @Nullable HeroProgress findHeroStats(GameMode mode, String heroName) {
        ModeStats stats = modeStats.get(mode);
        return stats == null ? null : stats.findHeroStats(heroName);
    }

    /** Total games (wins + losses) per hero across every mode. */
    public Map<String, Integer> gamesPerHero() {
        Map<String, Integer> games = new LinkedHashMap<>();
        for (ModeStats ms : modeStats.values()) {
            for (Map.Entry<String, HeroProgress> e : ms.heroStats().entrySet()) {
                games.merge(e.getKey(), e.getValue().wins() + e.getValue().losses(), Integer::sum);
            }
        }
        return games;
    }

    public HeroSummary summarizeHero(String hero, @Nullable GameMode mode) {
        List<GameMode> modes = mode != null ? List.of(mode) : List.of(GameMode.values());
        int wins = 0, losses = 0, deaths = 0, kills = 0;
        double damage = 0, voidDamage = 0;
        Map<String, AbilityStats> abilities = new LinkedHashMap<>();
        for (GameMode m : modes) {
            HeroProgress progress = findHeroStats(m, hero);
            if (progress == null) continue;
            wins += progress.wins();
            losses += progress.losses();
            deaths += progress.deaths();
            AbilityStats modeTotals = progress.totals();
            kills += modeTotals.kills();
            damage += modeTotals.damage();
            voidDamage += modeTotals.voidDamage();
            for (Map.Entry<String, AbilityStats> e : progress.abilityStats().entrySet()) {
                abilities.merge(e.getKey(), e.getValue(), AbilityStats::combine);
            }
        }
        return new HeroSummary(wins, losses, deaths, kills, damage, voidDamage, abilities);
    }

    public AbilityLookup lookupAbility(String hero, @Nullable GameMode mode, String abilityToken) {
        List<GameMode> modes = mode != null ? List.of(mode) : List.of(GameMode.values());
        String wanted = normalizeAbilityToken(abilityToken);
        String matchedKey = null;
        AbilityStats totals = new AbilityStats();
        List<String> available = new ArrayList<>();
        for (GameMode m : modes) {
            HeroProgress progress = findHeroStats(m, hero);
            if (progress == null) continue;
            for (Map.Entry<String, AbilityStats> e : progress.abilityStats().entrySet()) {
                if (!available.contains(e.getKey())) available.add(e.getKey());
                if (normalizeAbilityToken(e.getKey()).equals(wanted)) {
                    matchedKey = e.getKey();
                    totals = totals.combine(e.getValue());
                }
            }
        }
        return new AbilityLookup(matchedKey, totals, available);
    }

    private static String normalizeAbilityToken(String s) {
        return s.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    /** The hero's GLOBAL level/XP pool — every mode's XP lands here. */
    public HeroLevel getHeroLevel(String heroName) {
        return heroLevels.computeIfAbsent(heroName.toLowerCase(), k -> new HeroLevel());
    }

    public Map<String, HeroLevel> heroLevels() { return heroLevels; }

    public AbilityStats getAbilityStats(GameMode mode, String heroName, String abilityName) {
        return getHeroStats(mode, heroName).getAbilityStats(abilityName);
    }

    public int totalWins() {
        return modeStats.values().stream().mapToInt(ModeStats::totalWins).sum();
    }

    public int totalLosses() {
        return modeStats.values().stream().mapToInt(ModeStats::totalLosses).sum();
    }

    public int totalDeaths() {
        return modeStats.values().stream().mapToInt(ModeStats::totalDeaths).sum();
    }

    public int totalKills() {
        return modeStats.values().stream().mapToInt(ModeStats::totalKills).sum();
    }

    public double totalDamage() {
        return modeStats.values().stream().mapToDouble(ModeStats::totalDamage).sum();
    }

    public double totalVoidDamage() {
        return modeStats.values().stream().mapToDouble(ModeStats::totalVoidDamage).sum();
    }

    /** Smash Level = sum of every non-admin-only hero's global level. Pure computation. */
    public int smashLevel() {
        return heroLevels.entrySet().stream()
                .filter(entry -> !HeroConfig.isAdminOnly(entry.getKey()))
                .mapToInt(entry -> entry.getValue().level())
                .sum();
    }

    public boolean isSeasonChampion() { return seasonChampion; }
    public void setSeasonChampion(boolean seasonChampion) { this.seasonChampion = seasonChampion; }

    public int getTokenBalance() { return tokenBalance; }
    public void setTokenBalance(int tokenBalance) { this.tokenBalance = tokenBalance; }
    public void addTokens(int n) { tokenBalance += n; }

    /** Spends {@code n} tokens; false (and no change) if the balance is short. */
    public boolean spendTokens(int n) {
        if (tokenBalance < n) return false;
        tokenBalance -= n;
        return true;
    }

    public boolean isHeroUnlocked(String heroName) { return unlockedHeroes.contains(heroName.toLowerCase()); }
    public void unlockHero(String heroName) { unlockedHeroes.add(heroName.toLowerCase()); }
    public Set<String> getUnlockedHeroes() { return unlockedHeroes; }
    public void setUnlockedHeroes(Set<String> heroes) {
        unlockedHeroes.clear();
        heroes.forEach(name -> unlockedHeroes.add(name.toLowerCase()));
    }

    /** The player's chosen skin for a hero; 'default' when none chosen. */
    public String getSkinForHero(String heroName) {
        return selectedSkins.getOrDefault(heroName.toLowerCase(), "default");
    }

    public void setSkinForHero(String heroName, String skinId) {
        selectedSkins.put(heroName.toLowerCase(), skinId);
    }

    public Map<String, String> selectedSkins() { return selectedSkins; }

    public int getElo(GameMode mode) {
        return elo.getOrDefault(mode, GameConfigManager.getStartingElo());
    }

    public void setElo(GameMode mode, int value) {
        if (mode.hasElo()) {
            elo.put(mode, value);
        }
    }

    /** The single rating floor in the codebase — EloCalculator deliberately does not apply one. */
    public void addElo(GameMode mode, int change) {
        if (mode.hasElo()) {
            int current = getElo(mode);
            setElo(mode, Math.max(GameConfigManager.getMinElo(), current + change));
        }
    }

    public void setCurrentContext(GameMode mode, String heroName) {
        this.currentMode = mode;
        this.currentHero = heroName;
    }

    /**
     * Every damage/kill row is filed under the ability that CAUSED it, passed in by the caller.
     * These used to read a sticky {@code lastAbilityUsed} field that only the named damage sites
     * wrote: the unnamed sites filed under whatever was named last ("Jab", usually), and the field
     * was never cleared on a hero swap, so ability names leaked ACROSS heroes. A null name means
     * the caller had nothing to attribute, and the row is simply not written.
     */
    public void recordKill(String abilityName) {
        if (currentMode != null && currentHero != null && abilityName != null) {
            getAbilityStats(currentMode, currentHero, abilityName).addKill();
        }
    }

    public void recordDamage(String abilityName, double amount) {
        if (currentMode != null && currentHero != null && abilityName != null) {
            getAbilityStats(currentMode, currentHero, abilityName).addDamage(amount);
        }
    }

    public void recordVoidDamage(String abilityName, double amount) {
        if (currentMode != null && currentHero != null && abilityName != null) {
            getAbilityStats(currentMode, currentHero, abilityName).addVoidDamage(amount);
        }
    }

    public void recordDeath() {
        if (currentMode != null && currentHero != null) {
            getHeroStats(currentMode, currentHero).addDeath();
        }
    }

    public void recordWin() {
        if (currentMode != null && currentHero != null) {
            getHeroStats(currentMode, currentHero).addWin();
        }
    }

    public void recordLoss() {
        if (currentMode != null && currentHero != null) {
            getHeroStats(currentMode, currentHero).addLoss();
        }
    }

    public UUID playerId() { return playerId; }
    public String lastKnownName() { return lastKnownName; }
    public void setLastKnownName(String name) { this.lastKnownName = name; }
    public long lastSeen() { return lastSeen; }
    public void setLastSeen(long time) { this.lastSeen = time; }
    public String selectedHero() { return selectedHero; }
    public void setSelectedHero(String heroName) { this.selectedHero = heroName; }
    public Map<GameMode, Integer> elo() { return elo; }
    public Map<GameMode, ModeStats> modeStats() { return modeStats; }
    public GameMode currentMode() { return currentMode; }
    public String currentHero() { return currentHero; }
    public Set<String> equippedCharms() { return equippedCharms; }
    public void setEquippedCharms(Set<String> charms) {
        equippedCharms.clear();
        equippedCharms.addAll(charms);
    }
}
