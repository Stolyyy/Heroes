package me.stolyy.heroes.progression;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
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

    // Unlock economy + per-hero skin choice/ownership + skin shards (keyed by lowercase hero name).
    private int tokenBalance;
    private final Set<String> unlockedHeroes = new HashSet<>();
    private final Map<String, String> selectedSkins = new HashMap<>();
    private final Map<String, Set<String>> ownedSkins = new HashMap<>();
    private final Map<ShardKind, Map<HeroSkin.Rarity, Integer>> shards = new EnumMap<>(ShardKind.class);
    private int shardPity;

    // Global hero level/XP pool (mode-agnostic) — keyed by lowercase hero name.
    private final Map<String, HeroLevel> heroLevels = new HashMap<>();

    // Read by the async chat renderer, hence volatile.
    private volatile boolean seasonChampion;
    private volatile boolean chatPingsEnabled = true;

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

    public boolean isChatPingsEnabled() { return chatPingsEnabled; }
    public void setChatPingsEnabled(boolean chatPingsEnabled) { this.chatPingsEnabled = chatPingsEnabled; }

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
        return selectedSkins.getOrDefault(heroName.toLowerCase(Locale.ROOT), "default");
    }

    public void setSkinForHero(String heroName, String skinId) {
        String key = heroName.toLowerCase(Locale.ROOT);
        selectedSkins.put(key, skinId != null ? skinId.toLowerCase(Locale.ROOT) : "default");
    }

    public Map<String, String> selectedSkins() { return selectedSkins; }

    /** True when the player owns {@code skinId} for {@code heroName}. {@code default} is always owned. */
    public boolean ownsSkin(String heroName, String skinId) {
        String skin = skinId.toLowerCase(Locale.ROOT);
        if (skin.equals("default")) return true;
        Set<String> owned = ownedSkins.get(heroName.toLowerCase(Locale.ROOT));
        return owned != null && owned.contains(skin);
    }

    /** Grants ownership of {@code skinId} for {@code heroName}. {@code default} is never stored. Returns true if newly granted. */
    public boolean grantSkin(String heroName, String skinId) {
        String skin = skinId.toLowerCase(Locale.ROOT);
        if (skin.equals("default")) return false;
        return ownedSkins.computeIfAbsent(heroName.toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(skin);
    }

    /** Revokes ownership of {@code skinId} for {@code heroName}, resetting selection to {@code default} if equipped. */
    public boolean revokeSkin(String heroName, String skinId) {
        String hero = heroName.toLowerCase(Locale.ROOT);
        String skin = skinId.toLowerCase(Locale.ROOT);
        if (skin.equals("default")) return false;
        Set<String> owned = ownedSkins.get(hero);
        if (owned == null || !owned.remove(skin)) return false;
        if (owned.isEmpty()) ownedSkins.remove(hero);
        if (getSkinForHero(hero).equalsIgnoreCase(skin)) {
            setSkinForHero(hero, "default");
        }
        return true;
    }

    public Map<String, Set<String>> ownedSkins() { return ownedSkins; }

    public int getShardCount(ShardKind kind, HeroSkin.Rarity rarity) {
        Map<HeroSkin.Rarity, Integer> byRarity = shards.get(kind);
        return byRarity != null ? byRarity.getOrDefault(rarity, 0) : 0;
    }

    public void setShardCount(ShardKind kind, HeroSkin.Rarity rarity, int count) {
        if (count <= 0) {
            Map<HeroSkin.Rarity, Integer> byRarity = shards.get(kind);
            if (byRarity != null) {
                byRarity.remove(rarity);
                if (byRarity.isEmpty()) shards.remove(kind);
            }
            return;
        }
        shards.computeIfAbsent(kind, k -> new EnumMap<>(HeroSkin.Rarity.class)).put(rarity, count);
    }

    public void addShards(ShardKind kind, HeroSkin.Rarity rarity, int amount) {
        if (amount <= 0) return;
        setShardCount(kind, rarity, getShardCount(kind, rarity) + amount);
    }

    public boolean removeShards(ShardKind kind, HeroSkin.Rarity rarity, int amount) {
        if (amount <= 0) return false;
        int current = getShardCount(kind, rarity);
        if (current < amount) return false;
        setShardCount(kind, rarity, current - amount);
        return true;
    }

    public Map<ShardKind, Map<HeroSkin.Rarity, Integer>> shards() { return shards; }

    public int getShardPity() { return shardPity; }
    public void setShardPity(int shardPity) { this.shardPity = Math.max(0, shardPity); }
    public void incrementShardPity() { this.shardPity++; }
    public void resetShardPity() { this.shardPity = 0; }

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
