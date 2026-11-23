package me.stolyy.heroes.progression;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.physics.Interactions;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerStatsManager {
    private static File statsFolder;
    // Concurrent: the autosave and the async chat renderer read it off the main thread.
    private static final Map<UUID, PlayerStats> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private static BukkitTask autoSaveTask;

    /**
     * In-memory board index: one immutable {@link BoardEntry} snapshot per known stats file,
     * holding just the numbers the leaderboards rank on. Seeded from disk ONCE at startup;
     * from then on the folder is never walked again for a ranking — a game end only changes
     * the participants' numbers, and those live in {@link #cache}, which
     * {@link #boardPool()} overlays on top of this index at read time. The index only has to
     * be right for players NOT in the cache, whose numbers cannot change while they're offline;
     * {@link #saveStats} refreshes a player's snapshot at the same moment it persists them, so
     * the entry is current by the time {@link #unload} evicts them from the cache.
     *
     * <p>ConcurrentHashMap because the seed and the autosave publish from an async thread;
     * entries are immutable records, so cross-thread publication via the map is safe.
     */
    private static final Map<UUID, BoardEntry> boardIndex = new java.util.concurrent.ConcurrentHashMap<>();

    public static void initialize() {
        statsFolder = new File(Heroes.getInstance().getDataFolder(), "player-stats");
        if (!statsFolder.exists()) statsFolder.mkdirs();
        // Auto-save every 5 minutes (6000 ticks)
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            Heroes.getInstance(), PlayerStatsManager::saveAll, 6000L, 6000L);
        seedBoardIndexAsync();
    }

    /**
     * The one full disk walk, at startup, off-thread. Until it completes the boards can only
     * show cached (online) players — a refresh fires again on completion to backfill. Files
     * loaded here are thread-confined; only the immutable snapshots are published.
     */
    private static void seedBoardIndexAsync() {
        Heroes plugin = Heroes.getInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File[] files = statsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    UUID id;
                    try {
                        id = UUID.fromString(file.getName().replace(".yml", ""));
                    } catch (IllegalArgumentException ignored) { // non-uuid file name
                        continue;
                    }
                    PlayerStats stats = loadStats(id);
                    if (stats != null && stats.lastKnownName() != null) {
                        boardIndex.put(id, BoardEntry.of(stats));
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Boards may have spawned (and refreshed against a half-empty index) before the
                // seed landed — refresh again now that the offline players exist. Null-guarded
                // the same way Game.end guards these: the boards are optional lobby furniture.
                if (Heroes.getLeaderboardDisplay() != null) Heroes.getLeaderboardDisplay().refresh();
                if (Heroes.getLevelLeaderboardDisplay() != null) Heroes.getLevelLeaderboardDisplay().refresh();
            });
        });
    }

    public static PlayerStats getStats(UUID playerId) {
        return cache.computeIfAbsent(playerId, id -> {
            PlayerStats loaded = loadStats(id);
            return loaded != null ? loaded : new PlayerStats(id);
        });
    }

    /**
     * NPC backstop. Citizens NPCs (game bots, minions, practice dummies) are Player-typed and pass
     * through the server player list, so they reach every lobby/game path a real player does.
     * Caching stats for one writes a permanent file seeded with starting elo — most of the files
     * on disk are NPC uuids (game_bot_*, minion_*), one per spawn, forever.
     *
     * <p>They get a DETACHED PlayerStats: never entered into {@code cache}, therefore never
     * autosaved and never written to disk (saveStats/saveAll only walk the cache). Callers keep
     * working against a throwaway instead of the codebase growing a new NPC file every session.
     * Deliberately not a throw: bots legitimately reach this method through Game.start, and
     * killing game startup to police a stats file would be a far worse failure than a no-op.
     */
    public static PlayerStats getStats(Player player) {
        if (!Interactions.isRealPlayer(player)) return new PlayerStats(player.getUniqueId());

        PlayerStats stats = getStats(player.getUniqueId());
        stats.setLastKnownName(player.getName());
        stats.setLastSeen(System.currentTimeMillis());
        return stats;
    }

    private static PlayerStats loadStats(UUID playerId) {
        File file = new File(statsFolder, playerId + ".yml");
        if (!file.exists()) return null;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return deserializeStats(playerId, config);
    }

    /**
     * Resolves stats by player NAME, offline players included: online lookup first, then the
     * cache by last-known name, then a scan of the on-disk stat files. Offline results are NOT
     * entered into the write-back cache — the autosave loop would otherwise start rewriting
     * files for players who aren't on. Null when the name has never been seen.
     */
    public static PlayerStats lookupByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return getStats(online);

        for (PlayerStats stats : cache.values()) {
            if (name.equalsIgnoreCase(stats.lastKnownName())) return stats;
        }

        File[] files = statsFolder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return null;
        for (File file : files) {
            try {
                UUID id = UUID.fromString(file.getName().replace(".yml", ""));
                if (cache.containsKey(id)) continue; // cache already checked above
                PlayerStats stats = loadStats(id);
                if (stats != null && name.equalsIgnoreCase(stats.lastKnownName())) return stats;
            } catch (IllegalArgumentException ignored) {} // non-uuid file name
        }
        return null;
    }

    public static void saveStats(UUID playerId) {
        PlayerStats stats = cache.get(playerId);
        if (stats == null) return;

        File file = new File(statsFolder, playerId + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        serializeStats(stats, config);

        // Refresh the board snapshot wherever the persisted state is refreshed — this is what
        // keeps the index current for a player about to be evicted from the cache on quit.
        // On the async autosave thread this reads the same live fields serializeStats just read;
        // no new exposure beyond the pre-existing autosave pattern.
        boardIndex.put(playerId, BoardEntry.of(stats));

        try {
            config.save(file);
        } catch (IOException e) {
            Heroes.getInstance().getLogger().severe("Failed to save stats for " + playerId);
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        for (UUID playerId : cache.keySet()) {
            saveStats(playerId);
        }
    }

    public static void shutdown() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        saveAll();  // final synchronous save
    }

    public static void unload(UUID playerId) {
        saveStats(playerId);
        cache.remove(playerId);
    }

    private static void serializeStats(PlayerStats stats, FileConfiguration config) {
        config.set("player-id", stats.playerId().toString());
        config.set("last-known-name", stats.lastKnownName());
        config.set("last-seen", stats.lastSeen());
        config.set("selected-hero", stats.selectedHero());
        config.set("equipped-charms", stats.equippedCharms().stream().toList());
        config.set("token-balance", stats.getTokenBalance());
        config.set("season-champion", stats.isSeasonChampion());
        config.set("unlocked-heroes", new ArrayList<>(stats.getUnlockedHeroes()));
        for (Map.Entry<String, String> skinEntry : stats.selectedSkins().entrySet()) {
            config.set("selected-skins." + skinEntry.getKey(), skinEntry.getValue());
        }

        for (Map.Entry<GameMode, Integer> entry : stats.elo().entrySet()) {
            config.set("elo." + entry.getKey().name(), entry.getValue());
        }

        // Global (mode-agnostic) hero level/XP pools.
        for (Map.Entry<String, HeroLevel> levelEntry : stats.heroLevels().entrySet()) {
            String path = "hero-levels." + levelEntry.getKey();
            config.set(path + ".level", levelEntry.getValue().level());
            config.set(path + ".xp", levelEntry.getValue().xp());
        }

        for (Map.Entry<GameMode, ModeStats> modeEntry : stats.modeStats().entrySet()) {
            String modePath = "modes." + modeEntry.getKey().name();
            ModeStats modeStats = modeEntry.getValue();

            for (Map.Entry<String, HeroProgress> heroEntry : modeStats.heroStats().entrySet()) {
                String heroPath = modePath + ".heroes." + heroEntry.getKey();
                HeroProgress heroProgress = heroEntry.getValue();

                config.set(heroPath + ".wins", heroProgress.wins());
                config.set(heroPath + ".losses", heroProgress.losses());
                config.set(heroPath + ".deaths", heroProgress.deaths());

                for (Map.Entry<String, AbilityStats> abilityEntry : heroProgress.abilityStats().entrySet()) {
                    String abilityPath = heroPath + ".abilities." + abilityEntry.getKey();
                    AbilityStats abilityStats = abilityEntry.getValue();

                    config.set(abilityPath + ".kills", abilityStats.kills());
                    config.set(abilityPath + ".damage", abilityStats.damage());
                    config.set(abilityPath + ".void-damage", abilityStats.voidDamage());
                }
            }
        }
    }

    private static PlayerStats deserializeStats(UUID playerId, FileConfiguration config) {
        PlayerStats stats = new PlayerStats(playerId);
        stats.setLastKnownName(config.getString("last-known-name"));
        stats.setLastSeen(config.getLong("last-seen"));
        stats.setSelectedHero(config.getString("selected-hero"));
        List<String> charmNames = config.getStringList("equipped-charms");
        stats.setEquippedCharms(new HashSet<>(charmNames));

        // Unlock economy. Missing keys (pre-progression files) fall back to the starting
        // seed — that IS the migration path for existing players (B25.3).
        stats.setTokenBalance(config.getInt("token-balance", ProgressionConfig.getStartingTokens()));
        stats.setSeasonChampion(config.getBoolean("season-champion", false));
        List<String> unlocked = config.getStringList("unlocked-heroes");
        if (unlocked.isEmpty()) {
            stats.setUnlockedHeroes(new HashSet<>(ProgressionConfig.getStartingHeroes()));
        } else {
            stats.setUnlockedHeroes(new HashSet<>(unlocked));
        }
        ConfigurationSection skinsSection = config.getConfigurationSection("selected-skins");
        if (skinsSection != null) {
            for (String heroName : skinsSection.getKeys(false)) {
                stats.setSkinForHero(heroName, skinsSection.getString(heroName));
            }
        }

        ConfigurationSection eloSection = config.getConfigurationSection("elo");
        if (eloSection != null) {
            for (String key : eloSection.getKeys(false)) {
                try {
                    GameMode mode = GameMode.valueOf(key);
                    stats.setElo(mode, eloSection.getInt(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Global hero level/XP pools.
        ConfigurationSection levelsSection = config.getConfigurationSection("hero-levels");
        if (levelsSection != null) {
            for (String heroName : levelsSection.getKeys(false)) {
                HeroLevel heroLevel = stats.getHeroLevel(heroName);
                heroLevel.setLevel(levelsSection.getInt(heroName + ".level"));
                heroLevel.setXp(levelsSection.getInt(heroName + ".xp"));
            }
        }

        ConfigurationSection modesSection = config.getConfigurationSection("modes");
        if (modesSection != null) {
            for (String modeName : modesSection.getKeys(false)) {
                try {
                    GameMode mode = GameMode.valueOf(modeName);
                    ConfigurationSection heroesSection = modesSection.getConfigurationSection(modeName + ".heroes");
                    if (heroesSection == null) continue;

                    for (String heroName : heroesSection.getKeys(false)) {
                        HeroProgress heroProgress = stats.getHeroStats(mode, heroName);
                        ConfigurationSection heroSection = heroesSection.getConfigurationSection(heroName);
                        if (heroSection == null) continue;

                        heroProgress.setWins(heroSection.getInt("wins"));
                        heroProgress.setLosses(heroSection.getInt("losses"));
                        heroProgress.setDeaths(heroSection.getInt("deaths"));

                        // Migration (one-time): files from the per-mode-XP era carry level/xp
                        // under each mode-hero. Convert to cumulative XP via the curve and
                        // pour every mode's total into the hero's global pool.
                        if (levelsSection == null && (heroSection.contains("level") || heroSection.contains("xp"))) {
                            int totalXp = cumulativeXp(heroSection.getInt("level"), heroSection.getInt("xp"));
                            if (totalXp > 0) stats.getHeroLevel(heroName).addXp(totalXp);
                        }

                        ConfigurationSection abilitiesSection = heroSection.getConfigurationSection("abilities");
                        if (abilitiesSection != null) {
                            for (String abilityName : abilitiesSection.getKeys(false)) {
                                AbilityStats abilityStats = heroProgress.getAbilityStats(abilityName);
                                ConfigurationSection abilitySection = abilitiesSection.getConfigurationSection(abilityName);
                                if (abilitySection == null) continue;

                                abilityStats.setKills(abilitySection.getInt("kills"));
                                abilityStats.setDamage(abilitySection.getDouble("damage"));
                                abilityStats.setVoidDamage(abilitySection.getDouble("void-damage"));
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return stats;
    }

    /** Total XP represented by being {@code level} with {@code xpIntoLevel} spare (curve sum). */
    private static int cumulativeXp(int level, int xpIntoLevel) {
        long total = xpIntoLevel;
        for (int l = 0; l < level; l++) {
            int required = ProgressionConfig.getXpForLevel(l);
            if (required == Integer.MAX_VALUE) break; // past the curve cap
            total += required;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Top players by ELO for the given mode. Pure in-memory: the board index overlaid with the
     * live cache — no disk I/O, so this is safe to call synchronously on the tick path.
     */
    public static List<LeaderboardEntry> getTopPlayers(GameMode mode, int count) {
        return boardPool().stream()
                // Only entries with at least one finished game in this mode: hides seeded-elo
                // ghosts — stray bot/minion stat files (Citizens NPC uuids) and players who
                // never completed a ranked match.
                .filter(entry -> entry.playedModes().contains(mode))
                .map(entry -> new LeaderboardEntry(entry.name(), entry.elo().getOrDefault(mode, 0)))
                .sorted(Comparator.comparingInt(LeaderboardEntry::value).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Top players by TOTAL smash level, or by one hero's level when {@code heroName != null}.
     * Zero-level entries are dropped — that also hides bot/minion ghost files, which never earn
     * XP (awardXp is real-players-only). Pure in-memory, same as {@link #getTopPlayers}.
     */
    public static List<LeaderboardEntry> getTopByLevel(String heroName, int count) {
        return boardPool().stream()
                .map(entry -> new LeaderboardEntry(entry.name(),
                        heroName == null ? entry.smashLevel()
                                         : entry.heroLevels().getOrDefault(heroName, 0)))
                .filter(entry -> entry.value() > 0)
                .sorted(Comparator.comparingInt(LeaderboardEntry::value).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Top players by a cumulative stat in one mode, or summed across every mode when
     * {@code modeOrNull} is null. Players with 0 of the stat are dropped (that also hides
     * bot/minion ghost files). Pure in-memory, same as {@link #getTopPlayers}.
     */
    public static List<LeaderboardEntry> getTopByStat(Stat stat, GameMode modeOrNull, int count) {
        return boardPool().stream()
                .map(entry -> new LeaderboardEntry(entry.name(), entry.statValue(stat, modeOrNull)))
                .filter(entry -> entry.value() > 0)
                .sorted(Comparator.comparingInt(LeaderboardEntry::value).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * The ranking pool: every indexed (offline) player, with a FRESH snapshot taken for anyone
     * currently cached — a game that just ended changed only its participants' numbers, and they
     * are by definition in the cache, so the overlay is what makes the boards current without
     * ever re-reading a file. Main-thread only (reads live cached stats).
     */
    private static List<BoardEntry> boardPool() {
        Map<UUID, BoardEntry> pool = new HashMap<>(boardIndex);
        for (Map.Entry<UUID, PlayerStats> entry : cache.entrySet()) {
            if (entry.getValue().lastKnownName() != null) {
                pool.put(entry.getKey(), BoardEntry.of(entry.getValue()));
            }
        }
        return new ArrayList<>(pool.values());
    }

    /**
     * Immutable snapshot of the numbers the leaderboards rank on. Snapshotting (rather than
     * holding PlayerStats references) is what makes the index safe to publish across threads
     * and immune to later mutation of the live object.
     */
    private record BoardEntry(String name, Map<GameMode, Integer> elo, Set<GameMode> playedModes,
                              int smashLevel, Map<String, Integer> heroLevels,
                              Map<Stat, Map<GameMode, Integer>> stats) {

        static BoardEntry of(PlayerStats stats) {
            Map<GameMode, Integer> elo = new EnumMap<>(GameMode.class);
            Set<GameMode> played = EnumSet.noneOf(GameMode.class);
            Map<Stat, Map<GameMode, Integer>> modeTotals = new EnumMap<>(Stat.class);
            for (Stat stat : Stat.values()) {
                modeTotals.put(stat, new EnumMap<>(GameMode.class));
            }
            for (GameMode mode : GameMode.values()) {
                elo.put(mode, stats.getElo(mode));
                ModeStats modeStats = stats.getModeStats(mode);
                int games = modeStats.totalWins() + modeStats.totalLosses();
                if (games > 0) {
                    played.add(mode);
                }
                modeTotals.get(Stat.KILLS).put(mode, modeStats.totalKills());
                modeTotals.get(Stat.DEATHS).put(mode, modeStats.totalDeaths());
                modeTotals.get(Stat.WINS).put(mode, modeStats.totalWins());
                modeTotals.get(Stat.GAMES).put(mode, games);
            }
            Map<String, Integer> heroLevels = new HashMap<>();
            for (Map.Entry<String, HeroLevel> entry : stats.heroLevels().entrySet()) {
                heroLevels.put(entry.getKey(), entry.getValue().level());
            }
            return new BoardEntry(stats.lastKnownName(), elo, played, stats.smashLevel(), heroLevels, modeTotals);
        }

        /** One mode's value, or the sum across all modes when {@code modeOrNull} is null. */
        int statValue(Stat stat, GameMode modeOrNull) {
            Map<GameMode, Integer> perMode = stats.get(stat);
            if (modeOrNull != null) return perMode.get(modeOrNull);
            return perMode.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Cumulative counters the stat leaderboards rank on; GAMES is wins + losses. */
    public enum Stat {
        KILLS("Kills"), DEATHS("Deaths"), WINS("Wins"), GAMES("Games Played");

        private final String displayName;

        Stat(String displayName) { this.displayName = displayName; }

        public String displayName() { return displayName; }
    }

    /** One leaderboard row; {@code value} is elo or level depending on the query. */
    public record LeaderboardEntry(String name, int value) {}
}
