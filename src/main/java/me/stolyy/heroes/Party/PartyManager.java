package me.stolyy.heroes.Party;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {
    private final Map<UUID, Party> parties;
    private final Map<UUID, UUID> playerToParty;
    private final Map<UUID, UUID> invites;

    public PartyManager() {
        this.parties = new ConcurrentHashMap<>();
        this.playerToParty = new ConcurrentHashMap<>();
        this.invites = new ConcurrentHashMap<>();
    }

    public boolean invitePlayer(Player inviter, Player invited) {
        UUID inviterUUID = inviter.getUniqueId();
        UUID invitedUUID = invited.getUniqueId();

        if (isPlayerInParty(invitedUUID)) {
            inviter.sendMessage(Component.text("That player is already in a party.").color(NamedTextColor.RED));
            return false;
        }

        Party party = getPartyByPlayer(inviterUUID);
        if (party == null) {
            party = new Party(inviterUUID);
            parties.put(party.getId(), party);
            playerToParty.put(inviterUUID, party.getId());
        }

        invites.put(invitedUUID, party.getId());
        scheduleInviteExpiration(invitedUUID);

        inviter.sendMessage(Component.text("You have invited " + invited.getName() + " to your party.").color(NamedTextColor.GREEN));
        invited.sendMessage(Component.text("You've been invited to join " + inviter.getName() + "'s party! ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text("[Accept]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/party accept"))));
        return true;
    }

    public boolean acceptInvite(Player player) {
        UUID playerUUID = player.getUniqueId();
        UUID partyId = invites.remove(playerUUID);
        if (partyId == null) {
            player.sendMessage(Component.text("You don't have any pending invites.").color(NamedTextColor.RED));
            return false;
        }

        Party party = parties.get(partyId);
        if (party == null) {
            player.sendMessage(Component.text("The party you were invited to no longer exists.").color(NamedTextColor.RED));
            return false;
        }

        party.addMember(playerUUID);
        playerToParty.put(playerUUID, partyId);
        broadcastToParty(party, Component.text(player.getName() + " has joined the party!").color(NamedTextColor.GREEN));
        return true;
    }

    public void sendPartyMessage(Player sender, String message) {
        Party party = getPartyByPlayer(sender.getUniqueId());
        if (party == null) {
            sender.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return;
        }

        Component partyMessage = Component.text("[Party] " + sender.getName() + ": ").color(NamedTextColor.AQUA)
                .append(Component.text(message).color(NamedTextColor.WHITE));
        broadcastToParty(party, partyMessage);
    }

    public boolean leaveParty(Player player) {
        UUID playerUUID = player.getUniqueId();
        Party party = getPartyByPlayer(playerUUID);
        if (party == null) {
            player.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return false;
        }

        party.removeMember(playerUUID);
        playerToParty.remove(playerUUID);

        if (party.isEmpty()) {
            parties.remove(party.getId());
        } else if (party.getLeader().equals(playerUUID)) {
            UUID newLeader = party.getMembers().iterator().next();
            party.setLeader(newLeader);
            Player newLeaderPlayer = Bukkit.getPlayer(newLeader);
            if (newLeaderPlayer != null) {
                newLeaderPlayer.sendMessage(Component.text("You are now the party leader.").color(NamedTextColor.YELLOW));
            }
        }

        broadcastToParty(party, Component.text(player.getName() + " has left the party.").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("You have left the party.").color(NamedTextColor.YELLOW));
        return true;
    }

    private void broadcastToParty(Party party, Component message) {
        for (UUID memberUUID : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                member.sendMessage(message);
            }
        }
    }

    public boolean isPartyLeader(UUID playerId) {
        Party party = getPartyByPlayer(playerId);
        return party != null && party.getLeader().equals(playerId);
    }


    public boolean isPlayerInParty(UUID playerUUID) {
        return playerToParty.containsKey(playerUUID);
    }

    public Set<UUID> getPartyMembers(UUID playerUUID) {
        Party party = getPartyByPlayer(playerUUID);
        return party != null ? party.getMembers() : null;
    }

    public Party getPartyByPlayer(UUID playerUUID) {
        UUID partyId = playerToParty.get(playerUUID);
        return partyId != null ? parties.get(partyId) : null;
    }

    private void scheduleInviteExpiration(UUID invitedUUID) {
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            invites.remove(invitedUUID);
            Player invited = Bukkit.getPlayer(invitedUUID);
            if (invited != null) {
                invited.sendMessage(Component.text("Your party invite has expired.").color(NamedTextColor.YELLOW));
            }
        }, 20 * 60); // 60 seconds
    }

    public void clear() {
        parties.clear();
        playerToParty.clear();
        invites.clear();
    }

    public boolean disbandParty(Player leader) {
        Party party = getPartyByPlayer(leader.getUniqueId());
        if (party == null || !party.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(Component.text("You are not the leader of a party.").color(NamedTextColor.RED));
            return false;
        }

        for (UUID memberUUID : party.getMembers()) {
            playerToParty.remove(memberUUID);
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && !member.equals(leader)) {
                member.sendMessage(Component.text("The party has been disbanded.").color(NamedTextColor.YELLOW));
            }
        }

        parties.remove(party.getId());
        leader.sendMessage(Component.text("You have disbanded the party.").color(NamedTextColor.YELLOW));
        return true;
    }

    public boolean transferLeadership(Player currentLeader, Player newLeader) {
        Party party = getPartyByPlayer(currentLeader.getUniqueId());
        if (party == null || !party.getLeader().equals(currentLeader.getUniqueId())) {
            currentLeader.sendMessage(Component.text("You are not the leader of a party.").color(NamedTextColor.RED));
            return false;
        }

        if (!party.isMember(newLeader.getUniqueId())) {
            currentLeader.sendMessage(Component.text("The specified player is not in your party.").color(NamedTextColor.RED));
            return false;
        }

        party.setLeader(newLeader.getUniqueId());
        broadcastToParty(party, Component.text(newLeader.getName() + " is now the party leader.").color(NamedTextColor.YELLOW));
        return true;
    }
}