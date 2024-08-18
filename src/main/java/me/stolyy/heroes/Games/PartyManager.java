package me.stolyy.heroes.Games;

import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PartyManager {
    private Map<UUID, Set<UUID>> parties;
    private Map<UUID, InviteInfo> invites;

    public PartyManager() {
        parties = new HashMap<>();
        invites = new HashMap<>();
    }

    private class InviteInfo {
        UUID inviter;
        long expirationTime;

        InviteInfo(UUID inviter) {
            this.inviter = inviter;
            this.expirationTime = System.currentTimeMillis() + 60000; // 1 minute from now
        }
    }

    public boolean invitePlayer(Player inviter, Player invited) {
        UUID inviterUUID = inviter.getUniqueId();
        UUID invitedUUID = invited.getUniqueId();

        if (isPlayerInParty(inviterUUID) || isPlayerInParty(invitedUUID)) {
            return false;
        }

        invites.put(invitedUUID, new InviteInfo(inviterUUID));
        scheduleInviteExpiration(invitedUUID);
        return true;
    }

    private void scheduleInviteExpiration(UUID invitedUUID) {
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            InviteInfo invite = invites.get(invitedUUID);
            if (invite != null && System.currentTimeMillis() >= invite.expirationTime) {
                invites.remove(invitedUUID);
                Player invited = Bukkit.getPlayer(invitedUUID);
                if (invited != null) {
                    invited.sendMessage(Component.text("Your party invite has expired.").color(NamedTextColor.YELLOW));
                }
            }
        }, 20 * 60); // 60 seconds
    }

    public Set<UUID> acceptInvite(Player player) {
        UUID playerUUID = player.getUniqueId();
        InviteInfo invite = invites.get(playerUUID);
        if (invite == null || System.currentTimeMillis() >= invite.expirationTime) {
            invites.remove(playerUUID);
            return null;
        }

        UUID inviterUUID = invite.inviter;
        Set<UUID> party = parties.getOrDefault(inviterUUID, new HashSet<>());
        party.add(inviterUUID);
        party.add(playerUUID);
        parties.put(inviterUUID, party);
        invites.remove(playerUUID);
        return party;  // Return the party members
    }

    public boolean isPlayerInParty(UUID playerUUID) {
        for (Set<UUID> party : parties.values()) {
            if (party.contains(playerUUID)) {
                return true;
            }
        }
        return false;
    }

    public UUID getPartyLeader(UUID playerUUID) {
        for (Map.Entry<UUID, Set<UUID>> entry : parties.entrySet()) {
            if (entry.getValue().contains(playerUUID)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Set<UUID> getPartyMembers(UUID leaderUUID) {
        return parties.get(leaderUUID);
    }

    public boolean isPartyLeader(UUID playerUUID) {
        return parties.containsKey(playerUUID);
    }

    public void removeInvite(UUID playerUUID) {
        invites.remove(playerUUID);
    }

    public void sendPartyMessage(Player sender, String message) {
        Set<UUID> partyMembers = getPartyMembers(sender.getUniqueId());
        if (partyMembers == null) {
            sender.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return;
        }

        Component partyMessage = Component.text("[Party] " + sender.getName() + ": ").color(NamedTextColor.AQUA)
                .append(Component.text(message).color(NamedTextColor.WHITE));

        for (UUID memberUUID : partyMembers) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                member.sendMessage(partyMessage);
            }
        }
    }

    public boolean leaveParty(Player player) {
        UUID playerUUID = player.getUniqueId();
        for (Map.Entry<UUID, Set<UUID>> entry : parties.entrySet()) {
            if (entry.getValue().contains(playerUUID)) {
                entry.getValue().remove(playerUUID);
                if (entry.getValue().size() == 1) {
                    parties.remove(entry.getKey());
                } else if (entry.getKey().equals(playerUUID)) {
                    UUID newLeader = entry.getValue().iterator().next();
                    parties.put(newLeader, entry.getValue());
                    parties.remove(entry.getKey());
                }
                return true;
            }
        }
        return false;
    }

    public boolean disbandParty(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (parties.containsKey(playerUUID)) {
            Set<UUID> partyMembers = parties.remove(playerUUID);
            for (UUID memberUUID : partyMembers) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && !member.equals(player)) {
                    member.sendMessage(Component.text("The party has been disbanded.").color(NamedTextColor.YELLOW));
                }
            }
            return true;
        }
        return false;
    }

    public boolean transferLeadership(Player currentLeader, Player newLeader) {
        UUID currentLeaderUUID = currentLeader.getUniqueId();
        UUID newLeaderUUID = newLeader.getUniqueId();
        if (parties.containsKey(currentLeaderUUID)) {
            Set<UUID> partyMembers = parties.get(currentLeaderUUID);
            if (partyMembers.contains(newLeaderUUID)) {
                parties.remove(currentLeaderUUID);
                parties.put(newLeaderUUID, partyMembers);
                return true;
            }
        }
        return false;
    }

    public void handlePlayerLeave(Player player) {
        UUID playerUUID = player.getUniqueId();
        for (Map.Entry<UUID, Set<UUID>> entry : parties.entrySet()) {
            Set<UUID> partyMembers = entry.getValue();
            if (partyMembers.contains(playerUUID)) {
                partyMembers.remove(playerUUID);

                if (entry.getKey().equals(playerUUID)) {
                    // This player was the leader
                    if (!partyMembers.isEmpty()) {
                        // Transfer leadership to the first person who joined
                        UUID newLeader = partyMembers.iterator().next();
                        parties.remove(playerUUID);
                        parties.put(newLeader, partyMembers);

                        Player newLeaderPlayer = Bukkit.getPlayer(newLeader);
                        if (newLeaderPlayer != null) {
                            newLeaderPlayer.sendMessage(Component.text("You are now the leader of the party.").color(NamedTextColor.YELLOW));
                        }

                        for (UUID memberUUID : partyMembers) {
                            Player member = Bukkit.getPlayer(memberUUID);
                            if (member != null && !member.equals(newLeaderPlayer)) {
                                member.sendMessage(Component.text(player.getName() + " has left the party. " + newLeaderPlayer.getName() + " is now the leader.").color(NamedTextColor.YELLOW));
                            }
                        }
                    } else {
                        // The party is now empty, so remove it
                        parties.remove(playerUUID);
                    }
                } else {
                    // This player was not the leader
                    for (UUID memberUUID : partyMembers) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            member.sendMessage(Component.text(player.getName() + " has left the party.").color(NamedTextColor.YELLOW));
                        }
                    }
                }
                break;
            }
        }
    }

    public int getPartySize(UUID playerUUID) {
        Set<UUID> partyMembers = getPartyMembers(playerUUID);
        return partyMembers == null ? 0 : partyMembers.size();
    }

    public boolean isPlayerInPartyOfSize(UUID playerUUID, int requiredSize) {
        int partySize = getPartySize(playerUUID);
        return partySize == requiredSize;
    }

    public boolean isPlayerInPartyOfAtLeastSize(UUID playerUUID, int minSize) {
        int partySize = getPartySize(playerUUID);
        return partySize >= minSize;
    }
}
