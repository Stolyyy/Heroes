package me.stolyy.heroes.Game.Party;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Set;

public class PartyManager {
    static Set<Party> parties;

    public static void invitePlayer(Player invited, Player invitee){

    }

    public static void acceptInvite(Player player){

    }

    public static void disbandParty(Player player){

    }

    public static void transferLeader(Player leader, Player newLeader){

    }

    public static void sendMessage(Player player, Component message){

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
        return false;
    }

    public static Party getPlayerParty(Player player){
        for(Party p : parties){
            if(p.isMember(player)) return p;
        }
        return null;
    }
}
