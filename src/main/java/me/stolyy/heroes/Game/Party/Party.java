package me.stolyy.heroes.Game.Party;

import org.bukkit.entity.Player;

import java.util.Set;

public class Party {
    public Party(Set<Player> members, Player leader) {
        this.members = members;
        this.leader = leader;
    }

    Player leader;
    Set<Player> members;

    public void invite(Player inviter, Player invited){
        if(!inviter.equals(leader)){
            inviter.sendMessage("You must be the party leader in order to invite people!");
            return;
        }

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
        members.add(player);
    }

    public void removePlayer(Player player) {
        members.remove(player);
        if(player.equals(leader)) leader = members.iterator().next();
    }

    public int getSize() {
        return members.size();
    }

    public boolean isMember(Player player){
        return members.contains(player);
    }
}
