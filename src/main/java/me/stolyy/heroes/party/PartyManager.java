package me.stolyy.heroes.party;

import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class PartyManager {
    private static final Set<Party> parties = new HashSet<>();
    private static final Map<Player, Set<Player>> invitesPerPlayer = new HashMap<>();
    private static final Map<Player, Party> playerPartyMap = new HashMap<>();
    private final static long EXPIRATION_TIME = 1200;

    public static void invitePlayer(Player inviter, Player invited){
        invitesPerPlayer.computeIfAbsent(invited, k -> new HashSet<>());

        if(!isInParty(inviter)){
            parties.add(new Party(new HashSet<>(Collections.singletonList(inviter)), inviter));
            inviter.sendMessage(Component.text("You have created a new party!").color(NamedTextColor.GREEN));
        }
        if(!isPartyLeader(inviter)){
            inviter.sendMessage("You must be the party leader in order to invite people!");
            return;
        }

        if (invitesPerPlayer.get(invited).contains(inviter)) {
            inviter.sendMessage("You have already invited this player to your party!");
            return;
        }

        invitesPerPlayer.get(invited).add(inviter);
        inviter.sendMessage(Component.text("You have invited " + invited.getName() + " to your party.").color(NamedTextColor.GREEN));
        invited.sendMessage(Component.text("You've been invited to join " + inviter.getName() + "'s party! ")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text("[Accept]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/party accept " + inviter.getName()))));

        new BukkitRunnable() {
            long count = 0;
            @Override
            public void run() {
                count += 5;
                if(count >= EXPIRATION_TIME) {
                    removeInvite(invited, inviter, RemoveReason.EXPIRED);
                    this.cancel();
                    return;
                }

                if(!isPartyLeader(inviter)) {
                    removeInvite(invited, inviter, RemoveReason.NOT_LEADER);
                    this.cancel();
                    return;
                }

                if(!inviter.isOnline()) {
                    removeInvite(invited, inviter, RemoveReason.INVITER_DISCONNECT);
                    this.cancel();
                    return;
                }

                if(!invited.isOnline()) {
                    removeInvite(invited, inviter, RemoveReason.INVITED_DISCONNECT);
                    this.cancel();
                    return;
                }

                if(getPlayersInParty(inviter).contains(invited)){
                    removeInvite(invited, inviter, RemoveReason.ACCEPTED);
                    this.cancel();
                    return;
                }

                Set<Player> inviterSet = invitesPerPlayer.get(invited);
                if(inviterSet == null || !inviterSet.contains(inviter)) {
                    removeInvite(invited, inviter, RemoveReason.NO_LONGER_EXISTS);
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 0, 5);
    }

    private enum RemoveReason{
        EXPIRED, NOT_LEADER, INVITER_DISCONNECT, INVITED_DISCONNECT, NO_LONGER_EXISTS, ACCEPTED
    }

    private static void removeInvite(Player invited, Player inviter, RemoveReason reason){
        Set<Player> inviterSet = invitesPerPlayer.get(invited);
        if(inviterSet != null){
            inviterSet.remove(inviter);
            if(inviterSet.isEmpty()){
                invitesPerPlayer.remove(invited);
            }
        }

        switch(reason){
            case EXPIRED -> {
                inviter.sendMessage(Component.text("Your invite to " + invited.getName() + " has expired.", NamedTextColor.RED));
                invited.sendMessage(Component.text("The party invite from " + inviter.getName() + " has expired.", NamedTextColor.RED));
            }
            case NOT_LEADER -> {
                inviter.sendMessage(Component.text("Your invite to " + invited.getName() + " has expired because you are no longer leader.", NamedTextColor.RED));
                invited.sendMessage(Component.text("The party invite from " + inviter.getName() + " has expired.", NamedTextColor.RED));
            }
            case INVITER_DISCONNECT -> invited.sendMessage(Component.text("The party invite from " + inviter.getName() + " has expired because the player has disconnected.", NamedTextColor.RED));
            case INVITED_DISCONNECT -> inviter.sendMessage(Component.text("Your invite to " + invited.getName() + " has expired because the player has disconnected.", NamedTextColor.RED));
            case NO_LONGER_EXISTS -> {
                inviter.sendMessage(Component.text("The invite no longer exists.", NamedTextColor.RED));
                invited.sendMessage(Component.text("The invite from " + inviter.getName() + " no longer exists.", NamedTextColor.RED));
            }
        }
        if (invitesPerPlayer.get(invited) != null && invitesPerPlayer.get(invited).isEmpty()) {
            invitesPerPlayer.remove(invited);
        }
    }

    public static void acceptInvite(String inviterName, Player invited){
        Player inviter = Bukkit.getServer().getPlayer(inviterName);


        if(invitesPerPlayer.get(invited) != null && !invitesPerPlayer.get(invited).contains(inviter)){
            invited.sendMessage(Component.text("You do not have any invites from " + inviter.getName() + "!", NamedTextColor.RED));
            return;
        }

        Party inviterParty = getPlayerParty(inviter);
        if(inviterParty == null){
            invited.sendMessage(Component.text(inviter.getName() + " is not in a party!", NamedTextColor.RED));
            return;
        }

        Party invitedParty = getPlayerParty(invited);
        if(invitedParty != null){
            if(invitedParty.getMembers().contains(inviter)) {
                invited.sendMessage(Component.text("You are already in " + invitedParty.getLeader().getName() + "'s party!", NamedTextColor.YELLOW));
                return;
            }
            invitedParty.removePlayer(invited);
            invited.sendMessage(Component.text("You left " + invitedParty.getLeader().getName() + "'s party", NamedTextColor.YELLOW));
            if(invitedParty.getMembers().isEmpty()) parties.remove(invitedParty);
        }

        if(invitesPerPlayer.get(invited) != null) invitesPerPlayer.get(invited).remove(inviter);
        if (invitesPerPlayer.get(invited).isEmpty()) invitesPerPlayer.remove(invited);
        inviterParty.addPlayer(invited);
    }

    public static void disbandParty(Player player){
        Party playerParty = getPlayerParty(player);
        if(playerParty == null){
            player.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }
        if(!isPartyLeader(player)){
            player.sendMessage(Component.text("Only the party leader can disband the party.", NamedTextColor.RED));
            return;
        }
        parties.remove(playerParty);
        for(Player member : playerParty.getMembers()) member.sendMessage(Component.text("PartyModeGUI has been disbanded!", NamedTextColor.RED));
        playerParty.setMembers(null);
        playerParty.setLeader(null);
    }

    public static void transferLeader(Player leader, Player newLeader){
        Party playerParty = getPlayerParty(leader);
        if(playerParty == null){
            leader.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }
        if(playerParty.getLeader() != leader){
            leader.sendMessage(Component.text("You are not the leader!", NamedTextColor.RED));
            return;
        }
        playerParty.setLeader(newLeader);
        for(Player member : playerParty.getMembers()) member.sendMessage(Component.text(leader.getName() + " is the new PartyC Leader!", NamedTextColor.YELLOW));
    }

    public static void sendMessage(Player player, Component message){
        Party playerParty = getPlayerParty(player);
        if(playerParty == null){
            player.sendMessage(Component.text("You aren't in a party!", NamedTextColor.RED));
            return;
        }
        for(Player member : playerParty.getMembers()) member.sendMessage("Party: " + message);
    }

    public static void leaveParty(Player player){
        Party playerParty = getPlayerParty(player);
        if(playerParty == null){
            player.sendMessage(Component.text("You aren't in a party!", NamedTextColor.RED));
            return;
        }
        playerParty.removePlayer(player);
        if(playerParty.getMembers().isEmpty()){
            parties.remove(playerParty);
        }
    }

    public static Set<Player> getPlayersInParty(Player player){
        Party party = getPlayerParty(player);

        return party != null ? party.getMembers() : new HashSet<>();
    }

    public static boolean isInParty(Player player){
        return getPlayerParty(player) != null;
    }

    public static int getPartySize(Player player){
        return getPlayerParty(player).getSize();
    }

    public static boolean isPartyLeader(Player player){
        Party party = getPlayerParty(player);
        if(party == null || party.getLeader() == null){
            return false;
        }
        return party.getLeader().equals(player);
    }

    //find a way to use a map later
    public static Party getPlayerParty(Player player){
        return playerPartyMap.get(player);
    }

    public static void setPlayerParty(Player player, Party party){
        playerPartyMap.put(player, party);
    }
}
