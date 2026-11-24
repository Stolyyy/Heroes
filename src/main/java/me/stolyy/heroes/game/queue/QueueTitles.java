package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/** Cycles per-player queue countdown titles for active queue entries. */
final class QueueTitles {
    private static int titleTickCounter = 0;

    private QueueTitles() {}

    static void update(Map<UUID, Set<QueueEntry>> playerQueues, Map<GameMode, Queue<QueueEntry>> queues) {
        titleTickCounter++;
        int cycleSeconds = GameConfigManager.getQueueTitleCycleInterval();

        Iterator<Map.Entry<UUID, Set<QueueEntry>>> iterator = playerQueues.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<QueueEntry>> mapEntry = iterator.next();
            Player player = Bukkit.getPlayer(mapEntry.getKey());

            if (player == null || !player.isOnline()) {
                for (QueueEntry qe : mapEntry.getValue()) {
                    queues.get(qe.mode()).removeIf(e -> e.playerId().equals(mapEntry.getKey()));
                }
                iterator.remove();
                continue;
            }

            if (AfkTracker.isWarned(mapEntry.getKey())) continue;

            Set<QueueEntry> entries = mapEntry.getValue();
            if (entries.isEmpty()) continue;

            List<QueueEntry> entryList = new ArrayList<>(entries);
            int displayIndex = (titleTickCounter / cycleSeconds) % entryList.size();
            QueueEntry displayEntry = entryList.get(displayIndex);

            String time = formatTime(displayEntry.getElapsedSeconds());
            GameMode displayMode = displayEntry.mode();
            Component title = GameConfigManager.getMessage("queue.title",
                    Map.of("mode", displayMode.displayName(),
                            "count", String.valueOf(queues.get(displayMode).size()),
                            "max", String.valueOf(displayMode.maxTotalPlayers())));
            Component subtitle = GameConfigManager.getMessage("queue.subtitle",
                    Map.of("time", time));

            Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO);
            player.showTitle(Title.title(title, subtitle, times));
        }
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
