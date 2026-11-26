package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Last-input clock per online player (fed by {@code ActivityListener}) and queue AFK warning/drop sweep.
 * Queued players and armed auto-queues share this sweep so idle lobbies never pop into empty matches.
 */
public final class AfkTracker {

    private static final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private static final Set<UUID> afkWarned = new HashSet<>();

    private AfkTracker() {}

    public static void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public static void forget(UUID playerId) {
        lastActivity.remove(playerId);
    }

    public static void clear() {
        lastActivity.clear();
        afkWarned.clear();
    }

    static boolean isWarned(UUID playerId) {
        return afkWarned.contains(playerId);
    }

    /** Warns or drops idle players across both active queues and armed auto-queue loops. */
    static void sweep(Set<UUID> queuedPlayers, Consumer<Player> dequeue, Predicate<Player> isQueued) {
        int timeout = timeoutSeconds();
        int warning = warningSeconds();

        Set<UUID> watched = new LinkedHashSet<>(queuedPlayers);
        watched.addAll(AutoQueue.armedPlayers());
        afkWarned.retainAll(watched);

        for (UUID uuid : watched) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            int idle = idleSeconds(player);

            if (idle >= timeout) {
                afkWarned.remove(uuid);
                Set<Player> partyMembers = PartyManager.getPartyMembers(player);
                boolean wasQueued = isQueued.test(player);
                dequeue.accept(player);
                boolean wasArmed = AutoQueue.stop(player);
                player.clearTitle();
                if (wasQueued) {
                    player.sendMessage(GameConfigManager.getMessage("queue.afk-removed",
                            Map.of("seconds", String.valueOf(timeout))));
                }
                if (wasArmed) {
                    player.sendMessage(GameConfigManager.getMessage("queue.auto.stopped-afk"));
                }
                for (Player member : partyMembers) {
                    if (member.equals(player) || isQueued.test(member)) continue;
                    member.clearTitle();
                    member.sendMessage(GameConfigManager.getMessage("queue.afk-party-removed",
                            Map.of("player", player.getName())));
                }
                continue;
            }

            int secondsLeft = timeout - idle;
            if (secondsLeft > warning) {
                afkWarned.remove(uuid);
                continue;
            }

            boolean firstWarning = afkWarned.add(uuid);
            Map<String, String> ph = Map.of("seconds", String.valueOf(secondsLeft));
            if (firstWarning) {
                player.sendMessage(GameConfigManager.getMessage("queue.afk-warning", ph));
            }
            player.showTitle(Title.title(
                    GameConfigManager.getMessage("queue.afk-warning-title", ph),
                    GameConfigManager.getMessage("queue.afk-warning-subtitle", ph),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)));
            if (firstWarning || secondsLeft <= 5) {
                SoundBuilder.of(warningSound()).volume(warningSoundVolume()).pitch(warningSoundPitch()).play(player);
            }
        }
    }

    public static int idleSeconds(Player player) {
        Long last = lastActivity.get(player.getUniqueId());
        if (last == null) {
            touch(player);
            return 0;
        }
        return (int) ((System.currentTimeMillis() - last) / 1000);
    }

    public static int timeoutSeconds() {
        return Configs.requireInt(queueSection(), "afk-timeout-seconds");
    }

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
