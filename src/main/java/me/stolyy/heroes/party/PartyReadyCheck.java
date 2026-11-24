package me.stolyy.heroes.party;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Party ready check ({@code /party ready <mode> [args]}): every online member answers Yes/No via
 * clickable chat buttons; once all have said yes the stored join action runs for the leader.
 * One check per party, replaced by a new {@code /party ready}.
 */
public final class PartyReadyCheck {

    private static final long TIMEOUT_TICKS = 60 * 20L;
    private static final Map<Party, PartyReadyCheck> checks = new HashMap<>();

    private final Party party;
    private final Consumer<Player> onReady;
    private final Set<UUID> yes = new HashSet<>();
    private BukkitTask timeout;

    private PartyReadyCheck(Party party, Consumer<Player> onReady) {
        this.party = party;
        this.onReady = onReady;
    }

    /** Starts a check for the leader's party. {@code label} is the human-readable mode text; {@code onReady} joins for the (current) leader. */
    public static void start(Player leader, String label, Consumer<Player> onReady) {
        Party party = PartyManager.getPlayerParty(leader);
        if (party == null) {
            leader.sendMessage(GameConfigManager.getMessage("party.ready.not-in-party"));
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(GameConfigManager.getMessage("party.ready.not-leader"));
            return;
        }

        PartyReadyCheck previous = checks.remove(party);
        if (previous != null) previous.timeout.cancel();

        PartyReadyCheck check = new PartyReadyCheck(party, onReady);
        check.yes.add(leader.getUniqueId());
        checks.put(party, check);
        check.timeout = Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            if (checks.get(party) != check) return;
            checks.remove(party);
            check.broadcast(GameConfigManager.getMessage("party.ready.expired"));
        }, TIMEOUT_TICKS);

        Map<String, String> placeholders = Map.of("leader", leader.getName(), "mode", label);
        Component prompt = GameConfigManager.getMessage("party.ready.prompt", placeholders)
                .appendSpace()
                .append(GameConfigManager.getMessage("party.ready.yes-button")
                        .clickEvent(ClickEvent.runCommand("/party readyvote yes")))
                .appendSpace()
                .append(GameConfigManager.getMessage("party.ready.no-button")
                        .clickEvent(ClickEvent.runCommand("/party readyvote no")));
        leader.sendMessage(GameConfigManager.getMessage("party.ready.started", placeholders));
        for (Player member : party.getOnlineMembers()) {
            if (!member.equals(leader)) member.sendMessage(prompt);
        }
        check.completeIfReady();
    }

    public static void vote(Player member, boolean accept) {
        Party party = PartyManager.getPlayerParty(member);
        PartyReadyCheck check = party == null ? null : checks.get(party);
        if (check == null) {
            member.sendMessage(GameConfigManager.getMessage("party.ready.no-active-check"));
            return;
        }
        if (check.yes.contains(member.getUniqueId())) {
            member.sendMessage(GameConfigManager.getMessage("party.ready.already-voted"));
            return;
        }

        Map<String, String> placeholders = Map.of("player", member.getName());
        if (!accept) {
            checks.remove(party);
            check.timeout.cancel();
            check.broadcast(GameConfigManager.getMessage("party.ready.declined", placeholders));
            return;
        }

        check.yes.add(member.getUniqueId());
        if (!check.completeIfReady()) {
            check.broadcast(GameConfigManager.getMessage("party.ready.accepted",
                    Map.of("player", member.getName(), "pending", check.pendingNames())));
        }
    }

    /** Leader override: joins now without waiting for the remaining answers. */
    public static void force(Player leader) {
        PartyReadyCheck check = leaderCheck(leader);
        if (check == null) return;
        checks.remove(check.party);
        check.timeout.cancel();
        check.broadcast(GameConfigManager.getMessage("party.ready.forced"));
        check.onReady.accept(leader);
    }

    public static void cancel(Player leader) {
        PartyReadyCheck check = leaderCheck(leader);
        if (check == null) return;
        checks.remove(check.party);
        check.timeout.cancel();
        check.broadcast(GameConfigManager.getMessage("party.ready.cancelled"));
    }

    private static PartyReadyCheck leaderCheck(Player leader) {
        Party party = PartyManager.getPlayerParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(GameConfigManager.getMessage("party.ready.not-leader"));
            return null;
        }
        PartyReadyCheck check = checks.get(party);
        if (check == null) leader.sendMessage(GameConfigManager.getMessage("party.ready.no-active-check"));
        return check;
    }

    private boolean completeIfReady() {
        if (!pending().isEmpty()) return false;
        checks.remove(party);
        timeout.cancel();
        broadcast(GameConfigManager.getMessage("party.ready.all-ready"));
        onReady.accept(Bukkit.getPlayer(party.getLeader()));
        return true;
    }

    private Set<Player> pending() {
        return party.getOnlineMembers().stream()
                .filter(p -> !yes.contains(p.getUniqueId()))
                .collect(Collectors.toSet());
    }

    private String pendingNames() {
        return pending().stream().map(Player::getName).sorted().collect(Collectors.joining(", "));
    }

    private void broadcast(Component message) {
        party.getOnlineMembers().forEach(p -> p.sendMessage(message));
    }

    public static void clear() {
        checks.values().forEach(c -> c.timeout.cancel());
        checks.clear();
    }
}
