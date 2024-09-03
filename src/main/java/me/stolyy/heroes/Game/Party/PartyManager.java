package me.stolyy.heroes.Game.Party;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Set;

public class PartyManager {
    Set<Party> parties;

    public void invitePlayer(Player invited, Player invitee){

    }

    public void acceptInvite(Player player){

    }

    public void disbandParty(Player player){

    }

    public void transferLeader(Player leader, Player newLeader){

    }

    public void sendMessage(Player player, Component message){

    }

    public boolean isPartyLeader(Player player){
        return false;
    }

    public Party getPlayerParty(Player player){
        return null;
    }
}
