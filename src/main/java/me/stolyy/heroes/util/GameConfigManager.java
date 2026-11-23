package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.PracticeArena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameConfigManager {
    private static FileConfiguration gameConfig;
    private static FileConfiguration messagesConfig;
    private static List<PracticeArena> practiceArenas;

    public static void reload() {
        Heroes plugin = Heroes.getInstance();
        gameConfig = loadOrCreateConfig(plugin, "game-config.yml");
        messagesConfig = loadOrCreateConfig(plugin, "messages.yml");
        practiceArenas = null;
        TemplateConfig.reload();
    }

    private static FileConfiguration loadOrCreateConfig(Heroes plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration getGameConfig() { return gameConfig; }
    public static FileConfiguration getMessagesConfig() { return messagesConfig; }

    public static int getCountdownTime() { return gameConfig.getInt("timing.countdown"); }
    /** Which column (0-8) of the custom game GUI holds the utility buttons. 8 = rightmost, 4 = middle. */
    public static int getCustomGuiUtilityColumn() { return gameConfig.getInt("custom-gui.utility-column", 8); }
    public static int getQueueTitleCycleInterval() { return gameConfig.getInt("queue.title-cycle-interval", 2); }
    public static int getRespawnTime() { return gameConfig.getInt("timing.respawn"); }
    public static int getCleanupDelay() { return gameConfig.getInt("timing.cleanup-delay"); }
    public static int getDefaultGameDuration() { return gameConfig.getInt("timing.default-game-duration"); }

    public static List<Vector> getSpawnOffsets() {
        List<Vector> offsets = new ArrayList<>();
        List<?> rawOffsets = gameConfig.getList("spawn-offsets");
        if (rawOffsets != null) {
            for (Object obj : rawOffsets) {
                if (obj instanceof List<?> coords && coords.size() >= 3) {
                    double x = ((Number) coords.get(0)).doubleValue();
                    double y = ((Number) coords.get(1)).doubleValue();
                    double z = ((Number) coords.get(2)).doubleValue();
                    offsets.add(new Vector(x, y, z));
                }
            }
        }
        return offsets;
    }

    // --- Practice arenas (B24) ---

    /** Every {@code practice-arenas} entry in yml order; the first is the default arena. */
    public static List<PracticeArena> getPracticeArenas() {
        if (practiceArenas == null) {
            if (!gameConfig.contains("practice-arenas")) {
                throw new IllegalStateException("Missing required config key 'practice-arenas'");
            }
            List<PracticeArena> parsed = new ArrayList<>();
            for (Map<?, ?> entry : gameConfig.getMapList("practice-arenas")) {
                parsed.add(PracticeArena.parse(new MemoryConfiguration().createSection("arena", entry)));
            }
            practiceArenas = parsed;
        }
        return practiceArenas;
    }

    /** The arena containing a location, or null when it is inside none of them. */
    public static PracticeArena arenaAt(Location loc) {
        for (PracticeArena arena : getPracticeArenas()) {
            if (arena.contains(loc)) return arena;
        }
        return null;
    }

    public static PracticeArena getPracticeArena(String name) {
        for (PracticeArena arena : getPracticeArenas()) {
            if (arena.name().equalsIgnoreCase(name)) return arena;
        }
        return null;
    }

    public static double getCrystalWidth() { return gameConfig.getDouble("crystals.width"); }
    public static double getCrystalHeight() { return gameConfig.getDouble("crystals.height"); }
    public static double getCrystalYOffset() { return gameConfig.getDouble("crystals.y-offset"); }
    public static int getCrystalSpawnDelayTicks() { return gameConfig.getInt("crystals.spawn-delay-ticks"); }
    public static int getCrystalDefaultInterval() { return gameConfig.getInt("crystals.default-interval"); }
    public static float getCrystalSpinSpeed() { return (float) gameConfig.getDouble("crystals.spin-speed"); }
    public static double getCrystalBobAmplitude() { return gameConfig.getDouble("crystals.bob-amplitude"); }
    public static double getCrystalBobSpeed() { return gameConfig.getDouble("crystals.bob-speed"); }

    // --- Bots ---

    /** Cap on bots addable via the custom game GUI's Add Bot button (map-change carryover ignores this). */
    public static int getMaxBotsPerGame() { return gameConfig.getInt("bots.max-per-game", 8); }

    // --- Rating (see EloCalculator) ---

    public static int getStartingElo() { return Configs.requireInt(gameConfig, "elo.starting-elo"); }
    public static int getMinElo() { return Configs.requireInt(gameConfig, "elo.min-elo"); }

    /** A 2v2 result is roughly half as much information about any one player, so its K is lower. */
    public static int getKFactor(GameMode mode) {
        return switch (mode) {
            case ONE_V_ONE -> Configs.requireInt(gameConfig, "elo.k-factor-1v1");
            case TWO_V_TWO -> Configs.requireInt(gameConfig, "elo.k-factor-2v2");
            case CUSTOM -> throw new IllegalArgumentException("CUSTOM games are unrated; they have no k-factor");
        };
    }

    /** Intra-team rating gap at which K reaches its maximum reduction. */
    public static double getEloGapCap() { return Configs.requireDouble(gameConfig, "elo.team-gap-damping.gap-cap"); }

    /** Fraction of K removed at (or beyond) the gap cap. */
    public static double getEloMaxGapReduction() {
        return Configs.requireDouble(gameConfig, "elo.team-gap-damping.max-reduction");
    }

    public static int getInt(String path, int def) { return gameConfig.getInt(path, def); }
    public static int getInt(String path) { return gameConfig.getInt(path); }
    public static String getString(String path) { return gameConfig.getString(path, ""); }
    public static boolean getBoolean(String path) { return gameConfig.getBoolean(path); }
    public static List<String> getStringList(String path) { return messagesConfig.getStringList(path); }
    public static long getLong(String path, long def) { return gameConfig.getLong(path, def); }

    public static Component getMessage(String path) {
        return getMessage(path, Map.of());
    }

    public static Component getMessage(String path, Map<String, String> placeholders) {
        String raw = messagesConfig.getString(path, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    public static List<Component> getMessageList(String path, Map<String, String> placeholders) {
        List<String> rawList = messagesConfig.getStringList(path);
        List<Component> result = new ArrayList<>();
        for (String raw : rawList) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            result.add(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        }
        return result;
    }

    public static String getMessageString(String path) {
        return messagesConfig.getString(path, "");
    }

    public static String getMessageString(String path, Map<String, String> placeholders) {
        String raw = messagesConfig.getString(path, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }
}
