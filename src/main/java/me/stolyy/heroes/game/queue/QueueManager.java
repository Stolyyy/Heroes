package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.RestartManager;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.progression.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public class QueueManager {

    private static final Map<GameMode, Queue<QueueEntry>> queues = new EnumMap<>(GameMode.class);
    private static final Map<UUID, Set<QueueEntry>> playerQueues = new HashMap<>();
    /** Mode of the game each online player is attached to as an actual participant. */
    private static final Map<UUID, GameMode> playedMode = new HashMap<>();
    private static BukkitTask titleTask;
    private static int titleTickCounter = 0;

    static {
        for (GameMode mode : GameMode.values()) {
            if (mode.hasElo()) {
                queues.put(mode, new LinkedList<>());
            }
        }
    }

    public static void initialize() {
        titleTask = Bukkit.getScheduler().runTaskTimer(
            Heroes.getInstance(),
            QueueManager::tick,
            20L, 20L
        );
    }

    /**
     * The one 1 Hz queue pulse. Order matters: lobby returns are detected first so the requeue
     * prompt reads the auto-queue state it is about to describe, and titles are painted last so a
     * player who just entered the queue shows the queue title rather than a stale countdown.
     */
    private static void tick() {
        removeAfkPlayers();
        detectLobbyReturns();
        AutoQueue.tick();
        updateTitles();
    }

    /** Players currently inside the AFK warning window; the warning title owns their screen. */
    private static final Set<UUID> afkWarned = new HashSet<>();

    /**
     * AFK sweep over everyone queued or with auto-queue armed. Idle time comes from
     * {@link AfkTracker}. Inside the last {@code afk-warning-seconds} before the timeout the player
     * gets a countdown title plus a sound (once at the start, then every second for the final five),
     * and any input ends it. At the timeout they leave every queue and their auto-queue stops; a
     * party member going idle dequeues the whole party, since the entry is the party's, and the
     * others are told who stalled it. Runs before AutoQueue.tick so an armed loop never re-queues
     * someone the sweep just dropped.
     */
    private static void removeAfkPlayers() {
        int timeout = AfkTracker.timeoutSeconds();
        int warning = AfkTracker.warningSeconds();

        Set<UUID> watched = new LinkedHashSet<>(playerQueues.keySet());
        watched.addAll(AutoQueue.armedPlayers());
        afkWarned.retainAll(watched);

        for (UUID uuid : watched) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            int idle = AfkTracker.idleSeconds(player);

            if (idle >= timeout) {
                afkWarned.remove(uuid);
                Set<Player> partyMembers = PartyManager.getPartyMembers(player);
                boolean wasQueued = isQueued(player);
                removeFromAllQueues(player);
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
                    if (member.equals(player) || isQueued(member)) continue;
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
                player.playSound(player.getLocation(), AfkTracker.warningSound(),
                    AfkTracker.warningSoundVolume(), AfkTracker.warningSoundPitch());
            }
        }
    }

    public static void shutdown() {
        if (titleTask != null && !titleTask.isCancelled()) {
            titleTask.cancel();
        }
        for (Set<QueueEntry> entries : playerQueues.values()) {
            for (QueueEntry entry : entries) {
                Player player = entry.getPlayer();
                if (player != null && player.isOnline()) {
                    player.clearTitle();
                }
            }
        }
        playerQueues.clear();
        for (Queue<QueueEntry> queue : queues.values()) {
            queue.clear();
        }
        playedMode.clear();
        AutoQueue.clear();
    }

    /**
     * Restart drain: empties every queue and disarms every auto-queue loop without per-player
     * chatter, returning the online players who lost a place so the caller can tell them once.
     */
    public static Set<Player> closeForRestart() {
        Set<UUID> affected = new LinkedHashSet<>(playerQueues.keySet());
        affected.addAll(AutoQueue.disarmAll());
        playerQueues.clear();
        for (Queue<QueueEntry> queue : queues.values()) {
            queue.clear();
        }
        Set<Player> players = new LinkedHashSet<>();
        for (UUID uuid : affected) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.clearTitle();
            players.add(player);
        }
        return players;
    }

    public static void joinQueue(Player player, GameMode mode) {
        if (RestartManager.isDraining()) {
            player.sendMessage(GameConfigManager.getMessage("restart.queue-closed"));
            return;
        }

        if (GameManager.isPlayerInGame(player) && !GameManager.isSpectating(player)) {
            player.sendMessage(GameConfigManager.getMessage("queue.cannot-queue-in-game"));
            return;
        }

        // Both mode gates count ONLINE members only. A party is never disbanded when it shrinks to
        // one, and a member who logs off is never removed, so getSize() reports stale slots that
        // can no longer be seated — gating on it lets a stale party block queueing forever.
        if (mode == GameMode.ONE_V_ONE) {
            Party party = PartyManager.getPlayerParty(player);
            if (party != null && party.getOnlineSize() > 1) {
                player.sendMessage(GameConfigManager.getMessage("queue.cannot-queue-in-party"));
                return;
            }
        }

        if (isQueuedFor(player, mode)) {
            player.sendMessage(GameConfigManager.getMessage("queue.already-queued",
                Map.of("mode", mode.displayName())));
            return;
        }

        if (mode == GameMode.TWO_V_TWO) {
            Party party = PartyManager.getPlayerParty(player);

            // A party of one is treated exactly like a solo player — it falls through to the solo
            // branch rather than forming a one-member party entry that updateQueue would silently
            // discard for not having 2 online members.
            if (party != null && party.getOnlineSize() > 1) {
                // Party queue: at most 2, leader only
                if (party.getOnlineSize() > 2) {
                    player.sendMessage(GameConfigManager.getMessage("queue.party-wrong-size-2v2"));
                    return;
                }
                if (!PartyManager.isPartyLeader(player)) {
                    player.sendMessage(GameConfigManager.getMessage("queue.leader-only"));
                    return;
                }
                // Reject if any party member is already in a game
                for (Player member : PartyManager.getPartyMembers(player)) {
                    if (GameManager.isPlayerInGame(member)) {
                        player.sendMessage(Component.text(
                                member.getName() + " is currently in a game.",
                                NamedTextColor.RED));
                        return;
                    }
                }
                UUID partyId = UUID.randomUUID();
                for (Player member : PartyManager.getPartyMembers(player)) {
                    QueueEntry entry = new QueueEntry(member.getUniqueId(), mode, System.currentTimeMillis(), partyId);
                    playerQueues.computeIfAbsent(member.getUniqueId(), k -> new HashSet<>()).add(entry);
                    queues.get(mode).add(entry);
                    member.sendMessage(GameConfigManager.getMessage("queue.joined",
                        Map.of("mode", mode.displayName())));
                }
            } else {
                // Solo queue for 2v2 — no party required, and a party with only one member online
                // lands here too
                QueueEntry entry = new QueueEntry(player.getUniqueId(), mode, System.currentTimeMillis(), null);
                playerQueues.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(entry);
                queues.get(mode).add(entry);
                player.sendMessage(GameConfigManager.getMessage("queue.joined",
                    Map.of("mode", mode.displayName())));
            }
        } else {
            QueueEntry entry = new QueueEntry(player.getUniqueId(), mode, System.currentTimeMillis(), null);
            playerQueues.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(entry);
            queues.get(mode).add(entry);
            player.sendMessage(GameConfigManager.getMessage("queue.joined",
                Map.of("mode", mode.displayName())));
        }

        updateQueue(mode);
    }

    public static void leaveQueue(Player player) {
        if (!isQueued(player)) return;
        removeFromAllQueues(player);
        player.sendMessage(GameConfigManager.getMessage("queue.left"));
        AutoQueue.stop(player);
    }

    public static void leaveQueue(Player player, GameMode mode) {
        Set<QueueEntry> entries = playerQueues.get(player.getUniqueId());
        if (entries == null) return;
        QueueEntry toRemove = entries.stream().filter(e -> e.mode() == mode).findFirst().orElse(null);
        if (toRemove == null) return;

        entries.remove(toRemove);
        if (entries.isEmpty()) playerQueues.remove(player.getUniqueId());
        queues.get(mode).removeIf(e -> e.playerId().equals(player.getUniqueId()));
        if (toRemove.partyId() != null) {
            removePartyFromModeQueue(toRemove.partyId(), mode);
        }
        player.sendMessage(GameConfigManager.getMessage("queue.left-mode",
            Map.of("mode", mode.displayName())));
        // Only the mode they walked out of — /leave 2v2 should not silence a 1v1 auto-queue.
        if (AutoQueue.armedMode(player) == mode) AutoQueue.stop(player);
        if (!isQueued(player)) player.clearTitle();
    }

    public static int queueSize(GameMode mode) {
        Queue<QueueEntry> entries = queues.get(mode);
        return entries == null ? 0 : entries.size();
    }

    public static void removeFromAllQueues(Player player) {
        Set<QueueEntry> entries = playerQueues.remove(player.getUniqueId());
        if (entries == null) return;
        for (QueueEntry entry : entries) {
            queues.get(entry.mode()).removeIf(e -> e.playerId().equals(player.getUniqueId()));
            if (entry.partyId() != null) {
                removePartyFromQueue(entry.partyId());
            }
        }
        player.clearTitle();
    }

    public static boolean isQueued(Player player) {
        Set<QueueEntry> entries = playerQueues.get(player.getUniqueId());
        return entries != null && !entries.isEmpty();
    }

    public static boolean isQueuedFor(Player player, GameMode mode) {
        Set<QueueEntry> entries = playerQueues.get(player.getUniqueId());
        return entries != null && entries.stream().anyMatch(e -> e.mode() == mode);
    }

    public static Set<GameMode> getQueuedModes(Player player) {
        Set<QueueEntry> entries = playerQueues.get(player.getUniqueId());
        if (entries == null) return Set.of();
        return entries.stream().map(QueueEntry::mode).collect(Collectors.toSet());
    }

    private static void updateQueue(GameMode mode) {
        Queue<QueueEntry> queue = queues.get(mode);

        if (mode == GameMode.ONE_V_ONE) {
            // Clean stale entries first
            queue.removeIf(e -> {
                Player p = e.getPlayer();
                if (p == null || !p.isOnline()) {
                    Set<QueueEntry> entries = playerQueues.get(e.playerId());
                    if (entries != null) {
                        entries.removeIf(qe -> qe.mode() == mode && qe.playerId().equals(e.playerId()));
                        if (entries.isEmpty()) playerQueues.remove(e.playerId());
                    }
                    return true;
                }
                return false;
            });

            if (queue.size() >= 2) {
                QueueEntry e1 = queue.poll();
                QueueEntry e2 = queue.poll();
                Player p1 = e1.getPlayer();
                Player p2 = e2.getPlayer();
                startMatch(List.of(p1), List.of(p2), mode);
            }
        } else if (mode == GameMode.TWO_V_TWO) {
            // Collect available parties and solos
            Set<UUID> seenPartyIds = new LinkedHashSet<>();
            List<QueueEntry> soloEntries = new ArrayList<>();

            for (QueueEntry entry : queue) {
                if (entry.partyId() != null) {
                    if (!seenPartyIds.contains(entry.partyId())) {
                        List<Player> members = getPartyPlayers(entry.partyId());
                        if (members.size() == 2) {
                            seenPartyIds.add(entry.partyId());
                        } else {
                            removePartyFromQueue(entry.partyId());
                        }
                    }
                } else {
                    Player p = entry.getPlayer();
                    if (p != null && p.isOnline()) {
                        soloEntries.add(entry);
                    } else {
                        Set<QueueEntry> playerEntries = playerQueues.get(entry.playerId());
                        if (playerEntries != null) {
                            playerEntries.removeIf(qe -> qe.mode() == mode && qe.playerId().equals(entry.playerId()));
                            if (playerEntries.isEmpty()) playerQueues.remove(entry.playerId());
                        }
                    }
                }
            }

            // Clean stale solo entries from queue
            queue.removeIf(e -> e.partyId() == null && (e.getPlayer() == null || !e.getPlayer().isOnline()));

            List<UUID> partyIds = new ArrayList<>(seenPartyIds);

            // Case 1: Two parties of 2
            if (partyIds.size() >= 2) {
                List<Player> team1 = getPartyPlayers(partyIds.get(0));
                List<Player> team2 = getPartyPlayers(partyIds.get(1));
                startMatch(team1, team2, mode);
                return;
            }

            // Case 2: One party + 2 solos
            if (partyIds.size() >= 1 && soloEntries.size() >= 2) {
                List<Player> partyTeam = getPartyPlayers(partyIds.get(0));
                Player solo1 = soloEntries.get(0).getPlayer();
                Player solo2 = soloEntries.get(1).getPlayer();
                startMatch(partyTeam, List.of(solo1, solo2), mode);
                return;
            }

            // Case 3: 4+ solos — take the first 4 by join time, then balance teams by elo:
            // highest + lowest vs the two in the middle.
            if (soloEntries.size() >= 4) {
                List<Player> byElo = soloEntries.stream()
                    .limit(4)
                    .map(QueueEntry::getPlayer)
                    .sorted(Comparator.comparingInt((Player px) -> PlayerStatsManager.getStats(px).getElo(mode)).reversed())
                    .toList();
                startMatch(List.of(byElo.get(0), byElo.get(3)), List.of(byElo.get(1), byElo.get(2)), mode);
            }
        }
    }

    private static List<Player> getPartyPlayers(UUID partyId) {
        List<Player> players = new ArrayList<>();
        for (Set<QueueEntry> entries : playerQueues.values()) {
            for (QueueEntry entry : entries) {
                if (partyId.equals(entry.partyId())) {
                    Player player = entry.getPlayer();
                    if (player != null && player.isOnline()) {
                        players.add(player);
                    }
                }
            }
        }
        return players;
    }

    private static void removePartyFromQueue(UUID partyId) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Set<QueueEntry>> entry : playerQueues.entrySet()) {
            for (QueueEntry qe : entry.getValue()) {
                if (partyId.equals(qe.partyId())) {
                    toRemove.add(entry.getKey());
                    break;
                }
            }
        }
        for (UUID playerId : toRemove) {
            Set<QueueEntry> entries = playerQueues.get(playerId);
            if (entries != null) {
                entries.removeIf(e -> partyId.equals(e.partyId()));
                if (entries.isEmpty()) playerQueues.remove(playerId);
            }
            for (Queue<QueueEntry> queue : queues.values()) {
                queue.removeIf(e -> e.playerId().equals(playerId) && partyId.equals(e.partyId()));
            }
        }
    }

    private static void removePartyFromModeQueue(UUID partyId, GameMode mode) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Set<QueueEntry>> entry : playerQueues.entrySet()) {
            for (QueueEntry qe : entry.getValue()) {
                if (partyId.equals(qe.partyId()) && qe.mode() == mode) {
                    toRemove.add(entry.getKey());
                    break;
                }
            }
        }
        for (UUID playerId : toRemove) {
            Set<QueueEntry> entries = playerQueues.get(playerId);
            if (entries != null) {
                entries.removeIf(e -> partyId.equals(e.partyId()) && e.mode() == mode);
                if (entries.isEmpty()) playerQueues.remove(playerId);
            }
            queues.get(mode).removeIf(e -> e.playerId().equals(playerId));
            Player member = Bukkit.getPlayer(playerId);
            if (member != null && member.isOnline()) {
                member.clearTitle();
                member.sendMessage(GameConfigManager.getMessage("queue.left-mode",
                    Map.of("mode", mode.displayName())));
            }
        }
    }

    private static void startMatch(List<Player> team1, List<Player> team2, GameMode mode) {
        List<Player> all = new ArrayList<>();
        all.addAll(team1);
        all.addAll(team2);
        for (Player player : all) {
            removeFromAllQueues(player);
            player.sendMessage(GameConfigManager.getMessage("queue.match-found"));
        }
        GameManager.startQueuedGame(team1, team2, mode);
    }

    /**
     * Watches for players arriving back in the lobby from a ranked game and sends the requeue
     * prompt once. Observed rather than hooked: the lobby-return paths (Game.end for a normal
     * finish, GameManager.leaveGame for an early exit, MatchSeries for a finished series) share no
     * single exit, so playedMode remembers the mode while they are in a game and fires when the
     * game reference disappears.
     */
    private static void detectLobbyReturns() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Game game = GameManager.getPlayerGame(player);
            if (game != null) {
                // Recorded only while they are an actual participant, so a pure watcher is never
                // prompted, while a player eliminated into the spectator team keeps the entry they
                // earned by playing.
                if (!GameManager.isSpectating(player)) playedMode.put(uuid, game.gameMode());
                continue;
            }
            GameMode mode = playedMode.remove(uuid);
            if (mode != null && mode.hasElo()) sendRequeuePrompt(player, mode);
        }
        // Players who quit mid-game never hit the branch above; drop them so a rejoin is clean.
        playedMode.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    /**
     * One-click requeue line. The label text lives in messages.yml and the click/hover are attached
     * here — the legacy '&' serializer behind {@code getMessage} can express colour but has no
     * syntax for events, so the split is forced.
     */
    public static void sendRequeuePrompt(Player player, GameMode mode) {
        Map<String, String> placeholders = Map.of("mode", mode.displayName());

        if (AutoQueue.isArmed(player)) {
            // Auto-queue already owns this moment and is painting a countdown title. A second
            // "join now" button beside it would just be two ways to say the same thing, so the
            // line becomes the cancel affordance the title tells them about.
            player.sendMessage(GameConfigManager.getMessage("queue.requeue.auto-active", placeholders)
                    .append(Component.space())
                    .append(GameConfigManager.getMessage("queue.requeue.cancel-button", placeholders)
                            .clickEvent(ClickEvent.runCommand("/autoqueue stop"))
                            .hoverEvent(HoverEvent.showText(
                                    GameConfigManager.getMessage("queue.requeue.cancel-hover", placeholders)))));
            return;
        }

        player.sendMessage(GameConfigManager.getMessage("queue.requeue.prompt", placeholders)
                .append(Component.space())
                .append(GameConfigManager.getMessage("queue.requeue.join-button", placeholders)
                        .clickEvent(ClickEvent.runCommand("/join " + mode.displayName()))
                        .hoverEvent(HoverEvent.showText(
                                GameConfigManager.getMessage("queue.requeue.join-hover", placeholders))))
                .append(Component.space())
                .append(GameConfigManager.getMessage("queue.requeue.auto-button", placeholders)
                        .clickEvent(ClickEvent.runCommand("/autoqueue " + mode.displayName()))
                        .hoverEvent(HoverEvent.showText(
                                GameConfigManager.getMessage("queue.requeue.auto-hover", placeholders)))));
    }

    private static void updateTitles() {
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

            // The AFK warning title owns the screen until they move; repainting the queue title
            // over it every second would hide the one thing they need to react to.
            if (afkWarned.contains(mapEntry.getKey())) continue;

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
        int mins = seconds / 60;
        int secs = seconds % 60;
        return mins + ":" + String.format("%02d", secs);
    }
}
