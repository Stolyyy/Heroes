package me.stolyy.heroes.party;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.queue.QueueManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coordinates player parties, invites, queue departure on roster changes, and invite expiration.
 */
public class PartyManager {

    private static final long INVITE_EXPIRATION = 60 * 1000; // 60 seconds
    private static final Map<UUID, Party> playerPartyMap = new HashMap<>();
    private static final Set<PartyInvite> pendingInvites = new LinkedHashSet<>();
    private static BukkitTask expirationTask;

    /** Starts the periodic task that expires unclaimed party invites. */
    public static void initialize() {
        expirationTask = Bukkit.getScheduler().runTaskTimer(
            Heroes.getInstance(), PartyManager::processExpiredInvites, 100L, 100L);
    }

    public static void sendMessage(Player sender, Component message) {
        Party party = getPlayerParty(sender.getUniqueId());
        if (party == null) {
            sender.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }

        Component formattedMessage = Component.text("[Party] ", NamedTextColor.BLUE)
                .append(Component.text(sender.getName() + ": ", NamedTextColor.GRAY))
                .append(message.colorIfAbsent(NamedTextColor.WHITE));

        party.getOnlineMembers().forEach(member -> member.sendMessage(formattedMessage));
    }

    /** Creates a new party led by {@code leader}, leaving any prior party first. */
    public static Party createParty(Player leader) {
        leaveParty(leader);
        Party party = new Party(leader);
        playerPartyMap.put(leader.getUniqueId(), party);
        leader.sendMessage(Component.text("You have created a new party.", NamedTextColor.GREEN));
        return party;
    }

    private static @Nullable Party leaderPartyFor(Player inviter) {
        UUID inviterId = inviter.getUniqueId();
        Party party = getPlayerParty(inviterId);
        if (party == null) {
            party = createParty(inviter);
        }
        if (!party.isLeader(inviterId)) {
            inviter.sendMessage(Component.text("You must be the party leader to invite players.", NamedTextColor.RED));
            return null;
        }
        return party;
    }

    /** Sends a party invite to {@code invited}, creating a party for {@code inviter} if needed. */
    public static void invitePlayer(Player inviter, Player invited) {
        Party party = leaderPartyFor(inviter);
        if (party == null) return;

        UUID inviterId = inviter.getUniqueId();
        UUID invitedId = invited.getUniqueId();
        if (party.isMember(invitedId)) {
            inviter.sendMessage(Component.text(invited.getName() + " is already in your party.", NamedTextColor.YELLOW));
            return;
        }

        if (hasPendingInvite(inviterId, invitedId)) {
            inviter.sendMessage(Component.text("You have already sent an invite to this player.", NamedTextColor.RED));
            return;
        }

        createInvite(inviter, invited, false);
        inviter.sendMessage(Component.text("You invited " + invited.getName() + " to your party.", NamedTextColor.GREEN));
    }

    /**
     * Invites every other online player at once. Deliberately does not reuse {@link #invitePlayer}:
     * that method reports each rejection to the inviter, which in a loop would bury the sender in
     * one line per player. Here the per-target skips are silent and the inviter gets a single summary.
     */
    public static void inviteAll(Player inviter) {
        Party party = leaderPartyFor(inviter);
        if (party == null) return;

        UUID inviterId = inviter.getUniqueId();
        int sent = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID onlineId = online.getUniqueId();
            if (onlineId.equals(inviterId)) continue;
            if (party.isMember(onlineId)) continue;
            if (hasPendingInvite(inviterId, onlineId)) continue;

            createInvite(inviter, online, true);
            sent++;
        }

        if (sent == 0) {
            inviter.sendMessage(Component.text("There is nobody else online to invite.", NamedTextColor.YELLOW));
        } else {
            inviter.sendMessage(Component.text(
                    "Invited " + sent + (sent == 1 ? " player" : " players") + " to your party.",
                    NamedTextColor.GREEN));
        }
    }

    private static boolean hasPendingInvite(UUID inviterId, UUID invitedId) {
        return pendingInvites.stream()
                .anyMatch(i -> i.inviter().equals(inviterId) && i.invited().equals(invitedId));
    }

    // The invite itself — identical for targeted and bulk invites so the invitee cannot tell them apart.
    private static void createInvite(Player inviter, Player invited, boolean bulk) {
        pendingInvites.add(new PartyInvite(
                inviter.getUniqueId(), invited.getUniqueId(), System.currentTimeMillis(), bulk));

        invited.sendMessage(
                Component.text("You've been invited to join " + inviter.getName() + "'s party! ", NamedTextColor.YELLOW)
                        .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/party accept " + inviter.getName())))
        );
    }

    /** Accepts a pending invite from {@code inviter}, leaving any prior party and all queues. */
    public static void acceptInvite(Player invited, Player inviter) {
        UUID inviterId = inviter.getUniqueId();
        UUID invitedId = invited.getUniqueId();

        boolean inviteFound = pendingInvites.removeIf(i -> i.inviter().equals(inviterId) && i.invited().equals(invitedId));

        if (!inviteFound) {
            invited.sendMessage(Component.text("You do not have a pending invite from " + inviter.getName() + ".", NamedTextColor.RED));
            return;
        }

        Party partyToJoin = getPlayerParty(inviterId);
        if (partyToJoin == null) {
            invited.sendMessage(Component.text(inviter.getName() + "'s party no longer exists.", NamedTextColor.RED));
            return;
        }

        leaveParty(invited);
        partyToJoin.addMember(invitedId);
        playerPartyMap.put(invitedId, partyToJoin);
        QueueManager.removeFromAllQueues(invited);

        Component joinMessage = Component.text(invited.getName() + " has joined the party!", NamedTextColor.YELLOW);
        partyToJoin.getOnlineMembers().forEach(member -> member.sendMessage(joinMessage));
    }

    private static void processExpiredInvites() {
        long now = System.currentTimeMillis();
        List<PartyInvite> expired = new ArrayList<>();
        for (PartyInvite invite : pendingInvites) {
            if (now - invite.timestamp() > INVITE_EXPIRATION) {
                expired.add(invite);
            }
        }
        if (expired.isEmpty()) return;
        pendingInvites.removeAll(expired);
        expired.forEach(PartyManager::notifyExpiredInvite);
    }

    private static void notifyExpiredInvite(PartyInvite invite) {
        Player inviter = Bukkit.getPlayer(invite.inviter());
        Player invited = Bukkit.getPlayer(invite.invited());
        // One /party invite-all can expire a dozen invites in the same tick; the sender does not
        // need a line for each, but every invitee still gets their own notice.
        if (inviter != null && !invite.bulk()) inviter.sendMessage(Component.text("Your party invite to " + (invited != null ? invited.getName() : "an offline player") + " has expired.", NamedTextColor.RED));
        if (invited != null) invited.sendMessage(Component.text("Your party invite from " + (inviter != null ? inviter.getName() : "an offline player") + " has expired.", NamedTextColor.RED));
    }

    /** Removes {@code player} from their current party and notifies remaining members. */
    public static void leaveParty(Player player) {
        UUID playerId = player.getUniqueId();
        Party party = getPlayerParty(playerId);

        if (party == null) {
            return;
        }

        Component leaveMessage = Component.text(player.getName() + " has left the party.", NamedTextColor.YELLOW);
        party.getOnlineMembers().stream()
                .filter(member -> !member.equals(player))
                .forEach(member -> member.sendMessage(leaveMessage));

        removeFromParty(party, player);
        player.sendMessage(Component.text("You have left the party.", NamedTextColor.YELLOW));
    }

    /** Kicks {@code target} from {@code leader}'s party and drops them from any active queue. */
    public static void kickPlayer(Player leader, Player target) {
        UUID leaderId = leader.getUniqueId();
        Party party = getPlayerParty(leaderId);

        if (party == null || !party.isLeader(leaderId)) {
            leader.sendMessage(Component.text("You are not the leader of this party.", NamedTextColor.RED));
            return;
        }

        UUID targetId = target.getUniqueId();
        if (targetId.equals(leaderId)) {
            leader.sendMessage(Component.text("You cannot kick yourself. Use /party leave or /party disband.", NamedTextColor.RED));
            return;
        }

        if (!party.isMember(targetId)) {
            leader.sendMessage(Component.text(target.getName() + " is not in your party.", NamedTextColor.RED));
            return;
        }

        removeFromParty(party, target);

        target.sendMessage(Component.text("You have been kicked from the party.", NamedTextColor.RED));
        Component kickMessage = Component.text(target.getName() + " has been kicked from the party.", NamedTextColor.YELLOW);
        party.getOnlineMembers().forEach(member -> member.sendMessage(kickMessage));
    }

    private static void removeFromParty(Party party, Player player) {
        QueueManager.leaveQueue(player);
        party.removeMember(player.getUniqueId());
        playerPartyMap.remove(player.getUniqueId());
    }

    /** Transfers leadership of the current party to {@code newLeader}. */
    public static void transferLeader(Player currentLeader, Player newLeader) {
        UUID currentLeaderId = currentLeader.getUniqueId();
        UUID newLeaderId = newLeader.getUniqueId();
        Party party = getPlayerParty(currentLeaderId);

        if (party == null || !party.isLeader(currentLeaderId)) {
            currentLeader.sendMessage(Component.text("You are not the leader of this party.", NamedTextColor.RED));
            return;
        }

        if (!party.isMember(newLeaderId)) {
            currentLeader.sendMessage(Component.text(newLeader.getName() + " is not in your party.", NamedTextColor.RED));
            return;
        }

        party.setLeader(newLeaderId);
        Component message = Component.text(newLeader.getName() + " is the new party leader!", NamedTextColor.GOLD);
        party.getOnlineMembers().forEach(member -> member.sendMessage(message));
    }

    /** Disbands {@code leader}'s party, pulls online members out of queues, and clears all memberships. */
    public static void disbandParty(Player leader) {
        UUID leaderId = leader.getUniqueId();
        Party party = getPlayerParty(leaderId);

        if (party == null) {
            leader.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }

        if (!party.isLeader(leaderId)) {
            leader.sendMessage(Component.text("You are not the party leader.", NamedTextColor.RED));
            return;
        }

        Component disbandMessage = Component.text("The party has been disbanded.", NamedTextColor.RED);
        party.getOnlineMembers().forEach(member -> member.sendMessage(disbandMessage));
        party.getOnlineMembers().forEach(QueueManager::leaveQueue);
        // Include offline members so re-login does not resolve a stale party reference.
        new HashSet<>(party.getMemberUUIDs()).forEach(playerPartyMap::remove);
    }

    public static Party getPlayerParty(UUID playerUuid) {
        return playerPartyMap.get(playerUuid);
    }

    public static Party getPlayerParty(Player player) {
        return getPlayerParty(player.getUniqueId());
    }

    public static Set<Player> getPendingInvites(Player invitedPlayer) {
        UUID invitedId = invitedPlayer.getUniqueId();
        return pendingInvites.stream()
                .filter(i -> i.invited().equals(invitedId))
                .map(i -> Bukkit.getPlayer(i.inviter()))
                .filter(p -> p != null && p.isOnline())
                .collect(Collectors.toSet());
    }

    public static boolean isInParty(Player player) {
        return getPlayerParty(player.getUniqueId()) != null;
    }

    public static boolean isPartyLeader(Player player) {
        Party party = getPlayerParty(player.getUniqueId());
        return party != null && party.isLeader(player.getUniqueId());
    }

    public static Set<Player> getPartyMembers(Player player) {
        Party party = getPlayerParty(player.getUniqueId());
        return party != null ? party.getOnlineMembers() : Collections.emptySet();
    }

    /** Cancels expiration tasks and clears all party and invite state on shutdown. */
    public static void clear() {
        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
        }
        PartyReadyCheck.clear();
        playerPartyMap.clear();
        pendingInvites.clear();
    }
}
