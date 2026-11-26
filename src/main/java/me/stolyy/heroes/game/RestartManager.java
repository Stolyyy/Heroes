package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.ui.visual.Broadcast;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Graceful restarts. {@code drain} closes the queue, lets ranked matches finish (up to a cap) and
 * then cuts whatever is left; {@code now} skips straight to the countdown. Both end in
 * {@link Bukkit#shutdown()}, so the host (panel / wrapper script) is what brings the server back up.
 *
 * <p>{@link #isDraining()} is true from the moment either command starts, so joins stay rejected
 * through the final countdown too — a match that pops 20 seconds before shutdown is only a cut match.
 */
public final class RestartManager {
    private enum Phase { IDLE, DRAINING, COUNTDOWN }

    private static final Set<Integer> TITLE_MARKS = Set.of(30, 10, 5, 4, 3, 2, 1);

    private static Phase phase = Phase.IDLE;
    private static BukkitTask task;
    private static int drainCapSeconds;
    private static int announceSeconds;
    private static int elapsed;
    private static int remaining;

    private RestartManager() {}

    public static boolean isDraining() { return phase != Phase.IDLE; }

    public static boolean isCountingDown() { return phase == Phase.COUNTDOWN; }

    /** Closes queues, lets rated matches finish up to the drain cap, then begins the shutdown countdown. */
    public static void startDrain() {
        drainCapSeconds = Configs.requireInt(GameConfigManager.getGameConfig(), "restart.drain-cap-seconds");
        announceSeconds = Configs.requireInt(GameConfigManager.getGameConfig(), "restart.announce-seconds");
        phase = Phase.DRAINING;
        elapsed = 0;

        Title.Times times = Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500));
        for (Player player : QueueManager.closeForRestart()) {
            player.sendMessage(GameConfigManager.getMessage("restart.queue-dropped"));
            player.showTitle(Title.title(GameConfigManager.getMessage("restart.queue-dropped-title"),
                    GameConfigManager.getMessage("restart.queue-dropped-subtitle"), times));
        }

        Map<String, String> ph = Map.of("seconds", String.valueOf(drainCapSeconds));
        Broadcast.chat(GameConfigManager.getMessage("restart.drain-announce", ph));
        for (Game game : GameManager.getActiveGames()) {
            if (game.gameMode().hasElo()) continue;
            for (Player player : game.onlinePlayers()) {
                if (Interactions.isRealPlayer(player)) {
                    player.sendMessage(GameConfigManager.getMessage("restart.custom-game-warning", ph));
                }
            }
        }

        schedule();
    }

    /** Closes queues and begins the shutdown countdown immediately without cutting active matches first. */
    public static void startNow() {
        announceSeconds = Configs.requireInt(GameConfigManager.getGameConfig(), "restart.announce-seconds");
        for (Player player : QueueManager.closeForRestart()) {
            player.sendMessage(GameConfigManager.getMessage("restart.queue-dropped"));
        }
        beginCountdown(false);
    }

    private static void schedule() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), RestartManager::tick, 20L, 20L);
    }

    private static void tick() {
        if (phase == Phase.DRAINING) {
            elapsed++;
            boolean rankedLeft = GameManager.getActiveGames().stream().anyMatch(g -> g.gameMode().hasElo());
            if (!rankedLeft || elapsed >= drainCapSeconds) beginCountdown(true);
            return;
        }

        remaining--;
        if (remaining <= 0) {
            task.cancel();
            Bukkit.shutdown();
            return;
        }
        announceCountdown();
    }

    private static void beginCountdown(boolean cutGames) {
        phase = Phase.COUNTDOWN;
        if (cutGames) {
            for (Game game : new ArrayList<>(GameManager.getActiveGames())) {
                game.end(GameEndReason.CANCELLED);
            }
        }
        remaining = announceSeconds;
        announceCountdown();
        schedule();
    }

    private static void announceCountdown() {
        if (!TITLE_MARKS.contains(remaining) && remaining != announceSeconds) return;
        Map<String, String> ph = Map.of("seconds", String.valueOf(remaining));
        Broadcast.chatAndTitle(GameConfigManager.getMessage("restart.countdown", ph),
                GameConfigManager.getMessage("restart.countdown-title", ph),
                GameConfigManager.getMessage("restart.countdown-subtitle", ph),
                Duration.ofMillis(900));
    }
}
