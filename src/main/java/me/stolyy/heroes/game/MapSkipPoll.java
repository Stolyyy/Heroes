package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /skipmap} poll for a custom game: every participant votes Skip or Keep from clickable chat
 * buttons and the tally is re-sent on each vote. The poll only reports; the leader changes the map
 * by hand. State is keyed by the leader: a map change swaps the Game instance under a running
 * poll, so the leader's current game is always resolved live.
 */
public final class MapSkipPoll {

    private static final Map<UUID, MapSkipPoll> polls = new HashMap<>();

    private final UUID leaderId;
    private final String leaderName;
    private final Map<UUID, Boolean> votes = new LinkedHashMap<>(); // true = skip
    private final BukkitTask timeout;

    private MapSkipPoll(Player leader) {
        this.leaderId = leader.getUniqueId();
        this.leaderName = leader.getName();
        int seconds = Configs.requireInt(GameConfigManager.getGameConfig(), "poll.timeout-seconds");
        this.timeout = Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> end(true), seconds * 20L);
    }

    /** The custom game's leader: the party leader, or a seated partyless player when no party is in the game. */
    public static boolean isLeader(Player player, Game game) {
        if (game.gameMode() != GameMode.CUSTOM) return false;
        if (PartyManager.isInParty(player)) return PartyManager.isPartyLeader(player);
        if (game.playerTeam(player.getUniqueId()).color() == TeamColor.SPECTATOR) return false;
        return game.onlinePlayers().stream().noneMatch(PartyManager::isInParty);
    }

    public static MapSkipPoll of(Player leader) {
        return polls.get(leader.getUniqueId());
    }

    public static MapSkipPoll byLeaderName(String name) {
        Player leader = Bukkit.getPlayerExact(name);
        return leader == null ? null : polls.get(leader.getUniqueId());
    }

    public static MapSkipPoll start(Player leader) {
        MapSkipPoll poll = new MapSkipPoll(leader);
        polls.put(poll.leaderId, poll);
        poll.broadcast(GameConfigManager.getMessage("poll.skipmap.started", Map.of("leader", poll.leaderName)));
        poll.sendTally();
        return poll;
    }

    /** Records (or changes) a vote; false when the voter isn't in the poll's game. */
    public boolean vote(Player voter, boolean skip) {
        if (!participants().contains(voter)) return false;
        votes.put(voter.getUniqueId(), skip);
        sendTally();
        return true;
    }

    public void end(boolean timedOut) {
        polls.remove(leaderId);
        timeout.cancel();
        sendTally();
        broadcast(GameConfigManager.getMessage(timedOut ? "poll.skipmap.timed-out" : "poll.skipmap.ended"));
    }

    private Set<Player> participants() {
        Player leader = Bukkit.getPlayer(leaderId);
        Game game = leader == null ? null : GameManager.getPlayerGame(leader);
        return game == null ? Set.of() : game.onlinePlayers();
    }

    private void broadcast(Component message) {
        participants().forEach(p -> p.sendMessage(message));
    }

    private void sendTally() {
        Set<Player> participants = participants();
        String skip = namesFor(participants, true);
        String keep = namesFor(participants, false);
        String pending = participants.stream().filter(p -> !votes.containsKey(p.getUniqueId()))
                .map(Player::getName).sorted().collect(Collectors.joining(", "));
        long skipCount = participants.stream().filter(p -> Boolean.TRUE.equals(votes.get(p.getUniqueId()))).count();
        int percent = participants.isEmpty() ? 0 : (int) Math.round(100.0 * skipCount / participants.size());

        String none = GameConfigManager.getMessageString("poll.skipmap.none");
        Map<String, String> placeholders = Map.of(
                "skip", skip.isEmpty() ? none : skip,
                "keep", keep.isEmpty() ? none : keep,
                "pending", pending.isEmpty() ? none : pending,
                "percent", String.valueOf(percent),
                "skip_count", String.valueOf(skipCount),
                "total", String.valueOf(participants.size()));
        Component buttons = GameConfigManager.getMessage("poll.skipmap.skip-button")
                .clickEvent(ClickEvent.runCommand("/skipmap vote skip " + leaderName))
                .appendSpace()
                .append(GameConfigManager.getMessage("poll.skipmap.keep-button")
                        .clickEvent(ClickEvent.runCommand("/skipmap vote keep " + leaderName)));
        boolean open = polls.get(leaderId) == this;

        for (Player p : participants) {
            GameConfigManager.getMessageList("poll.skipmap.tally", placeholders).forEach(p::sendMessage);
            if (open) p.sendMessage(buttons);
        }
    }

    private String namesFor(Set<Player> participants, boolean skip) {
        return participants.stream()
                .filter(p -> Boolean.valueOf(skip).equals(votes.get(p.getUniqueId())))
                .map(Player::getName).sorted().collect(Collectors.joining(", "));
    }
}
