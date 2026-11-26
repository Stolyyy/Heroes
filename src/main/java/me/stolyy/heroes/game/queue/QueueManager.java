package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.RestartManager;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/** Ranked queue state, team matching, and queue admission gate. */
public class QueueManager {

    private static final Map<GameMode, Queue<QueueEntry>> queues = new EnumMap<>(GameMode.class);
    private static final Map<UUID, Set<QueueEntry>> playerQueues = new HashMap<>();
    private static BukkitTask titleTask;

    static {
        for (GameMode mode : GameMode.values()) {
            if (mode.hasElo()) {
                queues.put(mode, new LinkedList<>());
            }
        }
    }

    public static void initialize() {
        Bukkit.getPluginManager().registerEvents(new ActivityListener(), Heroes.getInstance());
        titleTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), QueueManager::tick, 20L, 20L);
    }

    private static void tick() {
        AfkTracker.sweep(playerQueues.keySet(), QueueManager::removeFromAllQueues, QueueManager::isQueued);
        RequeuePrompt.detectLobbyReturns();
        AutoQueue.tick();
        QueueTitles.update(playerQueues, queues);
    }

    /** Shared restart-drain gate for queue joins, custom game opens, and auto-queue arming. */
    public static boolean admit(Player player) {
        if (RestartManager.isDraining()) {
            player.sendMessage(GameConfigManager.getMessage("restart.queue-closed"));
            return false;
        }
        return true;
    }

    public static void shutdown() {
        if (titleTask != null && !titleTask.isCancelled()) {
            titleTask.cancel();
        }
        for (Set<QueueEntry> entries : playerQueues.values()) {
            for (QueueEntry entry : entries) {
                Player player = entry.getPlayer();
                if (player != null && player.isOnline()) player.clearTitle();
            }
        }
        playerQueues.clear();
        for (Queue<QueueEntry> queue : queues.values()) queue.clear();
        RequeuePrompt.clear();
        AfkTracker.clear();
        AutoQueue.clear();
    }

    public static Set<Player> closeForRestart() {
        Set<UUID> affected = new LinkedHashSet<>(playerQueues.keySet());
        affected.addAll(AutoQueue.disarmAll());
        playerQueues.clear();
        for (Queue<QueueEntry> queue : queues.values()) queue.clear();

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
        if (!admit(player)) return;

        if (GameManager.isPlayerInGame(player) && !GameManager.isSpectating(player)) {
            player.sendMessage(GameConfigManager.getMessage("queue.cannot-queue-in-game"));
            return;
        }

        if (mode == GameMode.ONE_V_ONE) {
            Party party = PartyManager.getPlayerParty(player);
            if (party != null && party.getOnlineSize() > 1) {
                player.sendMessage(GameConfigManager.getMessage("queue.cannot-queue-in-party"));
                return;
            }
        }

        if (isQueuedFor(player, mode)) {
            player.sendMessage(GameConfigManager.getMessage("queue.already-queued", Map.of("mode", mode.displayName())));
            return;
        }

        if (mode == GameMode.TWO_V_TWO) {
            Party party = PartyManager.getPlayerParty(player);
            if (party != null && party.getOnlineSize() > 1) {
                if (party.getOnlineSize() > 2) {
                    player.sendMessage(GameConfigManager.getMessage("queue.party-wrong-size-2v2"));
                    return;
                }
                if (!PartyManager.isPartyLeader(player)) {
                    player.sendMessage(GameConfigManager.getMessage("queue.leader-only"));
                    return;
                }
                for (Player member : PartyManager.getPartyMembers(player)) {
                    if (GameManager.isPlayerInGame(member)) {
                        player.sendMessage(Component.text(member.getName() + " is currently in a game.", NamedTextColor.RED));
                        return;
                    }
                }
                UUID partyId = UUID.randomUUID();
                for (Player member : PartyManager.getPartyMembers(player)) {
                    enqueue(member, mode, partyId);
                }
            } else {
                enqueue(player, mode, null);
            }
        } else {
            enqueue(player, mode, null);
        }

        updateQueue(mode);
    }

    private static void enqueue(Player player, GameMode mode, @Nullable UUID partyId) {
        QueueEntry entry = new QueueEntry(player.getUniqueId(), mode, System.currentTimeMillis(), partyId);
        playerQueues.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(entry);
        queues.get(mode).add(entry);
        player.sendMessage(GameConfigManager.getMessage("queue.joined", Map.of("mode", mode.displayName())));
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
        player.sendMessage(GameConfigManager.getMessage("queue.left-mode", Map.of("mode", mode.displayName())));
        if (AutoQueue.armedMode(player) == mode) AutoQueue.stop(player);
        if (!isQueued(player)) player.clearTitle();
    }

    /** Leaves {@code mode}'s queue, or disarms a matching auto-queue loop if not currently queued. */
    public static void leave(Player player, GameMode mode) {
        if (isQueuedFor(player, mode)) {
            leaveQueue(player, mode);
        } else if (AutoQueue.armedMode(player) == mode) {
            AutoQueue.stop(player);
        } else {
            player.sendMessage(GameConfigManager.getMessage("queue.not-in-queue",
                    Map.of("mode", mode.displayName())));
        }
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
            if (entry.partyId() != null) removePartyFromQueue(entry.partyId());
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

    private static void updateQueue(GameMode mode) {
        Queue<QueueEntry> queue = queues.get(mode);

        if (mode == GameMode.ONE_V_ONE) {
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
                Player p1 = queue.poll().getPlayer();
                Player p2 = queue.poll().getPlayer();
                startMatch(List.of(p1), List.of(p2), mode);
            }
        } else if (mode == GameMode.TWO_V_TWO) {
            Set<UUID> seenPartyIds = new LinkedHashSet<>();
            List<UUID> invalidPartyIds = new ArrayList<>();
            List<QueueEntry> soloEntries = new ArrayList<>();

            for (QueueEntry entry : queue) {
                if (entry.partyId() != null) {
                    if (!seenPartyIds.contains(entry.partyId()) && !invalidPartyIds.contains(entry.partyId())) {
                        List<Player> members = getPartyPlayers(entry.partyId());
                        if (members.size() == 2) seenPartyIds.add(entry.partyId());
                        else invalidPartyIds.add(entry.partyId());
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

            for (UUID partyId : invalidPartyIds) removePartyFromQueue(partyId);
            queue.removeIf(e -> e.partyId() == null && (e.getPlayer() == null || !e.getPlayer().isOnline()));

            List<UUID> partyIds = new ArrayList<>(seenPartyIds);
            if (partyIds.size() >= 2) {
                startMatch(getPartyPlayers(partyIds.get(0)), getPartyPlayers(partyIds.get(1)), mode);
                return;
            }
            if (!partyIds.isEmpty() && soloEntries.size() >= 2) {
                startMatch(getPartyPlayers(partyIds.get(0)),
                        List.of(soloEntries.get(0).getPlayer(), soloEntries.get(1).getPlayer()), mode);
                return;
            }
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
                    if (player != null && player.isOnline()) players.add(player);
                }
            }
        }
        return players;
    }

    private static void removePartyFromQueue(UUID partyId) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Set<QueueEntry>> entry : playerQueues.entrySet()) {
            if (entry.getValue().stream().anyMatch(qe -> partyId.equals(qe.partyId()))) {
                toRemove.add(entry.getKey());
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
            if (entry.getValue().stream().anyMatch(qe -> partyId.equals(qe.partyId()) && qe.mode() == mode)) {
                toRemove.add(entry.getKey());
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
                member.sendMessage(GameConfigManager.getMessage("queue.left-mode", Map.of("mode", mode.displayName())));
            }
        }
    }

    private static void startMatch(List<Player> team1, List<Player> team2, GameMode mode) {
        List<Player> all = new ArrayList<>(team1.size() + team2.size());
        all.addAll(team1);
        all.addAll(team2);
        for (Player player : all) {
            removeFromAllQueues(player);
            player.sendMessage(GameConfigManager.getMessage("queue.match-found"));
        }
        GameManager.startQueuedGame(team1, team2, mode);
    }
}
