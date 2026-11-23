package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last-input clock per online player, fed by {@link me.stolyy.heroes.listener.ActivityListener}.
 *
 * <p>Only the queue reads it: a player who stops touching the game while queued (or while
 * auto-queue is armed) is warned with a title and a sound, then dropped before a match can pop
 * around them, so an idle lobby never starts a 2v1. The timeout is long on purpose: queues can
 * sit for minutes and tabbing out while waiting is normal. It is not a server-wide AFK kick.
 *
 * <p>The map is concurrent because chat arrives on an async thread.
 */
public final class AfkTracker {

    private static final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    private AfkTracker() {}

    public static void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public static void forget(UUID playerId) {
        lastActivity.remove(playerId);
    }

    public static int idleSeconds(Player player) {
        Long last = lastActivity.get(player.getUniqueId());
        if (last == null) {
            // First sighting counts as activity; otherwise a player who joined before the
            // listener saw any input would read as idle since the epoch.
            touch(player);
            return 0;
        }
        return (int) ((System.currentTimeMillis() - last) / 1000);
    }

    public static boolean isAfk(Player player) {
        return idleSeconds(player) >= timeoutSeconds();
    }

    /** {@code queue.afk-timeout-seconds} from game-config.yml. */
    public static int timeoutSeconds() {
        return Configs.requireInt(queueSection(), "afk-timeout-seconds");
    }

    /** {@code queue.afk-warning-seconds}: how long before the timeout the warning starts. */
    public static int warningSeconds() {
        return Configs.requireInt(queueSection(), "afk-warning-seconds");
    }

    public static String warningSound() {
        return Configs.requireString(queueSection(), "afk-warning-sound");
    }

    public static float warningSoundVolume() {
        return (float) Configs.requireDouble(queueSection(), "afk-warning-sound-volume");
    }

    public static float warningSoundPitch() {
        return (float) Configs.requireDouble(queueSection(), "afk-warning-sound-pitch");
    }

    private static ConfigurationSection queueSection() {
        return Configs.requireSection(GameConfigManager.getGameConfig(), "queue");
    }
}
