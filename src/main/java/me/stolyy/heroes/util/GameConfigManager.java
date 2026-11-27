package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.PracticeArena;
import me.stolyy.heroes.vfx.effect.SoundEffect;
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

/**
 * Central loader and typed accessor for {@code game-config.yml} and {@code messages.yml}.
 */
public class GameConfigManager {
    private static FileConfiguration gameConfig;
    private static FileConfiguration messagesConfig;
    private static List<PracticeArena> practiceArenas;
    private static SoundEffect chatPingSound;

    /** Loads (or extracts defaults for) {@code game-config.yml}, {@code messages.yml}, and {@code templates.yml}. */
    public static void reload() {
        Heroes plugin = Heroes.getInstance();
        gameConfig = loadOrCreateConfig(plugin, "game-config.yml");
        messagesConfig = loadOrCreateConfig(plugin, "messages.yml");
        practiceArenas = null;
        chatPingSound = null;
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

    public static int getCountdownTime() { return Configs.requireInt(gameConfig, "timing.countdown"); }
    /** Which column (0-8) of the custom game GUI holds the utility buttons. 8 = rightmost, 4 = middle. */
    public static int getCustomGuiUtilityColumn() { return Configs.requireInt(gameConfig, "custom-gui.utility-column"); }
    public static int getQueueTitleCycleInterval() { return Configs.requireInt(gameConfig, "queue.title-cycle-interval"); }
    public static int getRespawnTime() { return Configs.requireInt(gameConfig, "timing.respawn"); }
    public static int getCleanupDelay() { return Configs.requireInt(gameConfig, "timing.cleanup-delay"); }
    public static int getDefaultGameDuration() { return Configs.requireInt(gameConfig, "timing.default-game-duration"); }

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

    // --- Practice arenas ---

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

    public static double getCrystalWidth() { return Configs.requireDouble(gameConfig, "crystals.width"); }
    public static double getCrystalHeight() { return Configs.requireDouble(gameConfig, "crystals.height"); }
    public static double getCrystalYOffset() { return Configs.requireDouble(gameConfig, "crystals.y-offset"); }
    public static int getCrystalSpawnDelayTicks() { return Configs.requireInt(gameConfig, "crystals.spawn-delay-ticks"); }
    public static int getCrystalDefaultInterval() { return Configs.requireInt(gameConfig, "crystals.default-interval"); }
    public static float getCrystalSpinSpeed() { return (float) Configs.requireDouble(gameConfig, "crystals.spin-speed"); }
    public static double getCrystalBobAmplitude() { return Configs.requireDouble(gameConfig, "crystals.bob-amplitude"); }
    public static double getCrystalBobSpeed() { return Configs.requireDouble(gameConfig, "crystals.bob-speed"); }

    // --- Bots ---

    /** Cap on bots addable via the custom game GUI's Add Bot button (map-change carryover ignores this). */
    public static int getMaxBotsPerGame() { return Configs.requireInt(gameConfig, "bots.max-per-game"); }

    // --- Team Balance ---

    /** Rolling window (in hours) of recent Custom matches used for auto-balance session scores. */
    public static double getTeamBalanceSessionWindowHours() {
        return Configs.requireDouble(gameConfig, "team-balance.session-window-hours");
    }

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

    // --- Chat pings ---

    public static String getChatPingHighlightColor() {
        return Configs.requireString(gameConfig, "chat.pings.highlight-color");
    }

    public static SoundEffect getChatPingSound() {
        if (chatPingSound == null) {
            chatPingSound = new SoundEffect(Configs.requireSection(gameConfig, "chat.pings.sound"));
        }
        return chatPingSound;
    }

    /** Formats a required message from {@code messages.yml} using legacy {@code &} color codes. */
    public static Component getMessage(String path) {
        return getMessage(path, Map.of());
    }

    /** Formats a required message from {@code messages.yml}, substituting {@code {key}} placeholders. */
    public static Component getMessage(String path, Map<String, String> placeholders) {
        String raw = Configs.requireString(messagesConfig, path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    /** Formats a required message list from {@code messages.yml}, substituting {@code {key}} placeholders. */
    public static List<Component> getMessageList(String path, Map<String, String> placeholders) {
        if (!messagesConfig.contains(path)) {
            throw new IllegalStateException("Missing required message list '" + path + "' in messages.yml");
        }
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

    /** Returns a required raw string from {@code messages.yml} without color deserialization. */
    public static String getMessageString(String path) {
        return Configs.requireString(messagesConfig, path);
    }
}
