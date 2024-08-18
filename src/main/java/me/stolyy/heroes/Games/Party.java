package me.stolyy.heroes.Games;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private final UUID id;
    private Player leader;
    private Set<Player> members;

    public Party(Player leader) {
        this.id = UUID.randomUUID();
        this.leader = leader;
        this.members = new HashSet<>();
        this.members.add(leader);
    }

    public UUID getId() {
        return id;
    }

    public Player getLeader() {
        return leader;
    }

    public void setLeader(Player leader) {
        this.leader = leader;
    }

    public Set<Player> getMembers() {
        return new HashSet<>(members);
    }

    public boolean addMember(Player player) {
        return members.add(player);
    }

    public boolean removeMember(Player player) {
        return members.remove(player);
    }

    public int getSize() {
        return members.size();
    }

    public boolean isMember(Player player) {
        return members.contains(player);
    }
}