package me.stolyy.heroes.Game.Party;

import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class PartyManager {
    static Set<Party> parties = new HashSet<>();
    //Invited, Inviters
    //clean up later by using concurrent map
    static Map<Player, Set<Player>> invitesPerPlayer = new HashMap<>();
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
            boolean stop = false;
            @Override
            public void run() {
                count += 5;

                //clean up later by using scheduler and listeners
                if(!invitesPerPlayer.get(invited).contains(inviter)) stop = removeInvite(invited, inviter, RemoveReason.NO_LONGER_EXISTS);
                if(count >= EXPIRATION_TIME) stop = removeInvite(invited, inviter, RemoveReason.EXPIRED);
                if(!isPartyLeader(inviter)) stop = removeInvite(invited, inviter, RemoveReason.NOTLEADER);
                if(!inviter.isOnline()) stop = removeInvite(invited, inviter, RemoveReason.INVITER_DISCONNECT);
                if(!invited.isOnline()) stop = removeInvite(invited, inviter, RemoveReason.INVITED_DISCONNECT);

                if(stop) this.cancel();
            }
        }.runTaskTimer(Heroes.getInstance(), 0, 5);
    }

    private enum RemoveReason{
        EXPIRED, NOTLEADER, INVITER_DISCONNECT, INVITED_DISCONNECT, NO_LONGER_EXISTS
    }

    private static boolean removeInvite(Player invited, Player inviter, RemoveReason reason){
        Set<Player> inviterSet = invitesPerPlayer.get(invited);
        if(inviterSet != null){
            inviterSet.remove(inviter);
            if(inviterSet.isEmpty()){
                invitesPerPlayer.remove(invited);
            }
        }

        switch(reason){
            case EXPIRED -> {
                inviter.sendMessage(Component.text("Your invite to " + invited.getName() + " has expired.").color(NamedTextColor.RED));
                invited.sendMessage(Component.text("The party invite from " + inviter.getName() + " has expired.").color(NamedTextColor.RED));
            }
            case NOTLEADER -> {
                inviter.sendMessage(Component.text("Your invite to " + invited.getName() + " has expired because you are no longer leader.").color(NamedTextColor.RED));
                invited.sendMessage(Component.text("The party invite from " + inviter.getName() + " has expired.").color(NamedTextColor.RED));
            }
            case INVITER_DISCONNECT -> invited.sendMessage(Component.text("The party invite from " + inviter.getName() + " has expired because the player has disconnected.").color(NamedTextColor.RED));
            case INVITED_DISCONNECT -> inviter.sendMessage(Component.text("Your invite to " + invited.getName() + " has expired because the player has disconnected.").color(NamedTextColor.RED));
            case NO_LONGER_EXISTS -> {
                inviter.sendMessage(Component.text("The invite no longer exists.").color(NamedTextColor.RED));
                invited.sendMessage(Component.text("The invite from " + inviter.getName() + " no longer exists.").color(NamedTextColor.RED));
            }
        }
        if (invitesPerPlayer.get(invited).isEmpty()) {
            invitesPerPlayer.remove(invited);
        }

        return true;
    }

    public static void acceptInvite(String inviterName, Player invited){
        Player inviter = Bukkit.getServer().getPlayer(inviterName);

        if(!invitesPerPlayer.get(invited).contains(inviter)){
            invited.sendMessage(Component.text("You do not have any invites from " + inviter.getName() + "!").color(NamedTextColor.RED));
            return;
        }

        Party inviterParty = getPlayerParty(inviter);
        if(inviterParty == null){
            invited.sendMessage(Component.text(inviter.getName() + " is not in a party!").color(NamedTextColor.RED));
            return;
        }

        Party invitedParty = getPlayerParty(invited);
        if(invitedParty != null){
            invitedParty.removePlayer(invited);
            invited.sendMessage(Component.text("You left " + invitedParty.getLeader().getName() + "'s party").color(NamedTextColor.YELLOW));
            if(invitedParty.getMembers().isEmpty()) parties.remove(invitedParty);
        }

        if(invitesPerPlayer.get(invited) != null) invitesPerPlayer.get(invited).remove(invited);
        if (invitesPerPlayer.get(invited).isEmpty()) invitesPerPlayer.remove(invited);
        inviterParty.addPlayer(invited);
    }

    public static void disbandParty(Player player){
        Party playerParty = getPlayerParty(player);
        if(playerParty == null){
            player.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return;
        }
        if(!isPartyLeader(player)){
            player.sendMessage(Component.text("Only the party leader can disband the party.").color(NamedTextColor.RED));
            return;
        }
        parties.remove(playerParty);
        for(Player member : playerParty.getMembers()) member.sendMessage(Component.text("Party has been disbanded!").color(NamedTextColor.RED));
        playerParty.setMembers(null);
        playerParty.setLeader(null);
        playerParty = null;
    }

    public static void transferLeader(Player leader, Player newLeader){
        Party playerParty = getPlayerParty(leader);
        if(playerParty == null){
            leader.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return;
        }
        if(playerParty.getLeader() != leader){
            leader.sendMessage(Component.text("You are not the leader!").color(NamedTextColor.RED));
            return;
        }
        playerParty.setLeader(newLeader);
        for(Player member : playerParty.getMembers()) member.sendMessage(Component.text(leader.getName() + " is the new PartyC Leader!").color(NamedTextColor.YELLOW));
    }

    public static void sendMessage(Player player, Component message){
        Party playerParty = getPlayerParty(player);
        if(playerParty == null){
            player.sendMessage(Component.text("You aren't in a party!").color(NamedTextColor.RED));
            return;
        }
        for(Player member : playerParty.getMembers()) member.sendMessage(Component.text("Party has been disbanded!").color(NamedTextColor.RED));
    }

    public static void leaveParty(Player player){
        Party playerParty = getPlayerParty(player);
        if(playerParty == null){
            player.sendMessage(Component.text("You aren't in a party!").color(NamedTextColor.RED));
            return;
        }
        playerParty.removePlayer(player);
        if(playerParty.getMembers().isEmpty()){
            parties.remove(playerParty);
        }
    }

    public static Set<Player> getPlayersInParty(Player player){
        return getPlayerParty(player).getMembers();
    }

    public static boolean isInParty(Player player){
        return getPlayerParty(player) != null;
    }

    public static int getPartySize(Player player){
        return getPlayerParty(player).getSize();
    }

    public static  boolean isPartyLeader(Player player){
        Party party = getPlayerParty(player);
        if(party == null || party.getLeader() == null){
            return false;
        }
        return party.getLeader().equals(player);
    }

    //find a way to use a map later
    public static Party getPlayerParty(Player player){
        for(Party p : parties){
            if(p.isMember(player)) return p;
        }
        return null;
    }
}
