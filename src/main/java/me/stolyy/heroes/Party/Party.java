package me.stolyy.heroes.Party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private final UUID id;
    private UUID leader;
    private final Set<UUID> members;

    public Party(UUID leader) {
        this.id = UUID.randomUUID();
        this.leader = leader;
        this.members = new HashSet<>();
        this.members.add(leader);
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        if (members.contains(leader)) {
            this.leader = leader;
        }
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public boolean addMember(UUID player) {
        return members.add(player);
    }

    public boolean removeMember(UUID player) {
        boolean removed = members.remove(player);
        if (removed && leader.equals(player) && !members.isEmpty()) {
            leader = members.iterator().next();
        }
        return removed;
    }

    public int getSize() {
        return members.size();
    }

    public boolean isMember(UUID player) {
        return members.contains(player);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}