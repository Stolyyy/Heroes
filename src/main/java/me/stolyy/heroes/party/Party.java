package me.stolyy.heroes.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Set;

public class Party {
    private Player leader;
    private Set<Player> members;

    public Party(Set<Player> members, Player leader) {
        this.members = members;
        this.leader = leader;
        for(Player member : members) PartyManager.setPlayerParty(member, this);
    }

    public Player getLeader() {
        return leader;
    }

    public void setLeader(Player leader) {
        if(members.contains(leader)) this.leader = leader;
    }

    public Set<Player> getMembers() {
        return members;
    }

    public void setMembers(Set<Player> members) {
        this.members = members;
    }

    public void addPlayer(Player player) {
        PartyManager.setPlayerParty(player, this);
        player.sendMessage(Component.text("You joined " + getLeader().getName() + "'s party").color(NamedTextColor.YELLOW));
        for(Player member : members) member.sendMessage(Component.text(player.getName() + " has joined the party!").color(NamedTextColor.YELLOW));
        members.add(player);
    }

    public void removePlayer(Player player) {
        if(!members.contains(player)){
            player.sendMessage(Component.text("You are not in the party!").color(NamedTextColor.RED));
            return;
        }
        PartyManager.setPlayerParty(player, null);
        members.remove(player);
        for(Player member : members) {
            member.sendMessage(Component.text(player.getName() + " has left the party!").color(NamedTextColor.YELLOW));
        }
        if(player.equals(leader)) {
            if(!members.isEmpty()){
                leader = members.iterator().next();
                for(Player member : members) {
                    member.sendMessage(Component.text(leader.getName() + " is the new Party Leader!").color(NamedTextColor.YELLOW));
                }
            } else {
                leader = null;
            }
        }
    }

    public int getSize() {
        return members.size();
    }

    public boolean isMember(Player player){
        if(members == null) return false;
        return members.contains(player);
    }
}
