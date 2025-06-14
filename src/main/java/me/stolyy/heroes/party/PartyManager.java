package me.stolyy.heroes.party;

import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class PartyManager {

    private static final Heroes plugin = Heroes.getInstance();
    private static final Map<UUID, Party> playerPartyMap = new HashMap<>();
    private static final Set<PartyInvite> pendingInvites = new LinkedHashSet<>();
    private static final BukkitTask expirationTask = Bukkit.getScheduler().runTaskTimer(plugin, PartyManager::processExpiredInvites, 100L, 100L);

    private static final long INVITE_EXPIRATION = 60 * 1000; // 60 seconds

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

    public static Party createParty(Player leader) {
        leaveParty(leader);
        Party party = new Party(leader);
        playerPartyMap.put(leader.getUniqueId(), party);
        leader.sendMessage(Component.text("You have created a new party.", NamedTextColor.GREEN));
        return party;
    }

    public static void invitePlayer(Player inviter, Player invited) {
        UUID inviterId = inviter.getUniqueId();
        UUID invitedId = invited.getUniqueId();

        Party party = getPlayerParty(inviterId);
        if (party == null) {
            party = createParty(inviter);
        }

        if (!party.isLeader(inviterId)) {
            inviter.sendMessage(Component.text("You must be the party leader to invite players.", NamedTextColor.RED));
            return;
        }

        if (party.isMember(invitedId)) {
            inviter.sendMessage(Component.text(invited.getName() + " is already in your party.", NamedTextColor.YELLOW));
            return;
        }

        if (pendingInvites.stream().anyMatch(i -> i.inviter().equals(inviterId) && i.invited().equals(invitedId))) {
            inviter.sendMessage(Component.text("You have already sent an invite to this player.", NamedTextColor.RED));
            return;
        }

        PartyInvite invite = new PartyInvite(inviterId, invitedId, System.currentTimeMillis());
        pendingInvites.add(invite);

        inviter.sendMessage(Component.text("You invited " + invited.getName() + " to your party.", NamedTextColor.GREEN));
        invited.sendMessage(
                Component.text("You've been invited to join " + inviter.getName() + "'s party! ", NamedTextColor.YELLOW)
                        .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/party accept " + inviter.getName())))
        );
    }

    public static void acceptInvite(Player invited, Player inviter) {
        if (inviter == null) {
            invited.sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED));
            return;
        }

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

        Component joinMessage = Component.text(invited.getName() + " has joined the party!", NamedTextColor.YELLOW);
        partyToJoin.getOnlineMembers().forEach(member -> member.sendMessage(joinMessage));
    }

    private static void processExpiredInvites() {
        long currentTime = System.currentTimeMillis();
        pendingInvites.removeIf(invite -> {
            if (currentTime - invite.timestamp() > INVITE_EXPIRATION) {
                Player inviter = Bukkit.getPlayer(invite.inviter());
                Player invited = Bukkit.getPlayer(invite.invited());
                if (inviter != null) inviter.sendMessage(Component.text("Your party invite to " + (invited != null ? invited.getName() : "an offline player") + " has expired.", NamedTextColor.RED));
                if (invited != null) invited.sendMessage(Component.text("Your party invite from " + (inviter != null ? inviter.getName() : "an offline player") + " has expired.", NamedTextColor.RED));
                return true;
            }
            return false;
        });
    }

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

        party.removeMember(playerId);
        playerPartyMap.remove(playerId);

        player.sendMessage(Component.text("You have left the party.", NamedTextColor.YELLOW));

        if (party.getSize() != 0) {
            if (party.isLeader(playerId)) {
                Player newLeader = Bukkit.getPlayer(party.getLeader());
                if (newLeader != null) {
                    Component newLeaderMessage = Component.text(newLeader.getName() + " is the new party leader.", NamedTextColor.GOLD);
                    party.getOnlineMembers().forEach(member -> member.sendMessage(newLeaderMessage));
                }
            }
        }
    }

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
        party.getOnlineMembers().forEach(member -> {
            member.sendMessage(disbandMessage);
            playerPartyMap.remove(member.getUniqueId());
        });
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

    public static void clear () {
        if (!expirationTask.isCancelled()) {
            expirationTask.cancel();
        }
    }
}
