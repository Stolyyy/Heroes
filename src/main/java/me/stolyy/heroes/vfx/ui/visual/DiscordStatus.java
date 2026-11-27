package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.DiscordWebhook;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Maintains a single live Discord status message edited in place via webhook, and posts
 * hero-mastery announcements when configured.
 */
public final class DiscordStatus {
    private static final String STATE_FILE_NAME = "discord-state.yml";
    private static final String MESSAGE_ID_KEY = "status-message-id";
    private static final Object STATE_LOCK = new Object();

    private static BukkitTask task;
    private static volatile String messageId;
    private static volatile String lastSentContent;
    private static final AtomicBoolean inFlight = new AtomicBoolean(false);

    private DiscordStatus() {}

    /** Point-in-time player and mode counts captured on the main thread. */
    public record Snapshot(
            List<String> onlinePlayers,
            int oneVOneQueued,
            int oneVOneInGame,
            int twoVTwoQueued,
            int twoVTwoInGame,
            int customInGame
    ) {}

    public static void start() {
        stopTask();
        lastSentContent = null;
        inFlight.set(false);
        loadState();

        String webhook = GameConfigManager.getDiscordStatusWebhook();
        if (webhook.isEmpty()) return;

        int intervalSeconds = GameConfigManager.getDiscordStatusUpdateSeconds();
        if (intervalSeconds <= 0) {
            throw new IllegalStateException("discord.status.update-seconds must be positive, got: " + intervalSeconds);
        }
        long periodTicks = intervalSeconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), DiscordStatus::tick, 20L, periodTicks);
    }

    public static void shutdown() {
        stopTask();
        String webhook = GameConfigManager.getDiscordStatusWebhook();
        String id = messageId;
        if (!webhook.isEmpty() && id != null && !id.isEmpty()) {
            String offlineText = GameConfigManager.getDiscordStatusOfflineText();
            DiscordWebhook.editSync(webhook, id, DiscordWebhook.contentPayload(offlineText));
        }
        lastSentContent = null;
        inFlight.set(false);
    }

    /** Captures live real-player online, queue, and match counts on the main thread. */
    public static Snapshot captureSnapshot() {
        List<String> online = Bukkit.getOnlinePlayers().stream()
                .filter(Interactions::isRealPlayer)
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        int oneVOneQueued = QueueManager.queueSize(GameMode.ONE_V_ONE);
        int twoVTwoQueued = QueueManager.queueSize(GameMode.TWO_V_TWO);
        int oneVOneInGame = 0;
        int twoVTwoInGame = 0;
        for (Game game : GameManager.getActiveGames()) {
            if (game.gameMode() == GameMode.ONE_V_ONE) {
                oneVOneInGame += countRealPlayers(game, false);
            } else if (game.gameMode() == GameMode.TWO_V_TWO) {
                twoVTwoInGame += countRealPlayers(game, false);
            }
        }

        int customInGame = 0;
        for (Game game : GameManager.getLiveGames()) {
            if (game.gameMode() == GameMode.CUSTOM && game.gameState() != GameState.ENDED) {
                customInGame += countRealPlayers(game, true);
            }
        }

        return new Snapshot(online, oneVOneQueued, oneVOneInGame, twoVTwoQueued, twoVTwoInGame, customInGame);
    }

    /** Renders {@code snapshot} into Discord message markdown using {@code messages.yml} templates. */
    public static String formatStatus(Snapshot snapshot) {
        String header = GameConfigManager.getMessageString("discord.status.header")
                .replace("{online}", String.valueOf(snapshot.onlinePlayers().size()));
        String line1v1 = GameConfigManager.getMessageString("discord.status.queue-line")
                .replace("{mode}", "1v1")
                .replace("{queued}", String.valueOf(snapshot.oneVOneQueued()))
                .replace("{ingame}", String.valueOf(snapshot.oneVOneInGame()));
        String line2v2 = GameConfigManager.getMessageString("discord.status.queue-line")
                .replace("{mode}", "2v2")
                .replace("{queued}", String.valueOf(snapshot.twoVTwoQueued()))
                .replace("{ingame}", String.valueOf(snapshot.twoVTwoInGame()));
        String lineCustom = GameConfigManager.getMessageString("discord.status.custom-line")
                .replace("{ingame}", String.valueOf(snapshot.customInGame()));

        StringBuilder sb = new StringBuilder();
        sb.append(header);
        if (!snapshot.onlinePlayers().isEmpty()) {
            String escapedNames = snapshot.onlinePlayers().stream()
                    .map(DiscordWebhook::escapeMarkdown)
                    .collect(Collectors.joining(", "));
            String listLine = GameConfigManager.getMessageString("discord.status.player-list")
                    .replace("{players}", escapedNames);
            sb.append('\n').append(listLine);
        }
        sb.append("\n\n").append(line1v1)
                .append('\n').append(line2v2)
                .append('\n').append(lineCustom);
        return sb.toString();
    }

    /** Posts a hero-mastery announcement to {@code discord.mastery-webhook} if configured. */
    public static void postMastery(String playerName, String heroDisplay, int level) {
        String webhook = GameConfigManager.getDiscordMasteryWebhook();
        if (webhook.isEmpty()) return;
        String text = GameConfigManager.getMessageString("discord.mastery")
                .replace("{player}", DiscordWebhook.escapeMarkdown(playerName))
                .replace("{hero}", DiscordWebhook.escapeMarkdown(heroDisplay))
                .replace("{level}", String.valueOf(level));
        DiscordWebhook.post(webhook, DiscordWebhook.contentPayload(text));
    }

    private static void tick() {
        String webhook = GameConfigManager.getDiscordStatusWebhook();
        if (webhook.isEmpty()) return;
        if (!inFlight.compareAndSet(false, true)) return;

        String content = formatStatus(captureSnapshot());
        if (content.equals(lastSentContent)) {
            inFlight.set(false);
            return;
        }

        String json = DiscordWebhook.contentPayload(content);
        String id = messageId;
        if (id == null || id.isEmpty()) {
            DiscordWebhook.postForId(webhook, json, newId -> {
                saveMessageId(newId);
                lastSentContent = content;
                inFlight.set(false);
            }, () -> inFlight.set(false));
        } else {
            DiscordWebhook.edit(webhook, id, json, result -> {
                switch (result) {
                    case OK -> lastSentContent = content;
                    case NOT_FOUND -> {
                        saveMessageId(null);
                        lastSentContent = null;
                    }
                    case FAILED -> {}
                }
                inFlight.set(false);
            });
        }
    }

    private static int countRealPlayers(Game game, boolean includeSpectators) {
        int count = 0;
        for (Player p : game.onlinePlayers(includeSpectators)) {
            if (Interactions.isRealPlayer(p)) count++;
        }
        return count;
    }

    private static void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static File stateFile() {
        return new File(Heroes.getInstance().getDataFolder(), STATE_FILE_NAME);
    }

    private static void loadState() {
        synchronized (STATE_LOCK) {
            File file = stateFile();
            if (!file.exists()) {
                messageId = null;
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String saved = yaml.getString(MESSAGE_ID_KEY, "");
            messageId = saved.isEmpty() ? null : saved;
        }
    }

    private static void saveMessageId(String newId) {
        synchronized (STATE_LOCK) {
            messageId = newId;
            File file = stateFile();
            YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            yaml.set(MESSAGE_ID_KEY, newId);
            try {
                yaml.save(file);
            } catch (IOException e) {
                Heroes.getInstance().getLogger().warning("Failed to save " + STATE_FILE_NAME + ": " + e);
            }
        }
    }
}
