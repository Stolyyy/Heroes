package me.stolyy.heroes.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class Party {
    private UUID leader;
    private final Set<UUID> members;

    public Party(Player leader) {
        this.leader = leader.getUniqueId();
        this.members = new LinkedHashSet<>();
        this.members.add(leader.getUniqueId());
    }

    protected void addMember(UUID playerUuid) {
        members.add(playerUuid);
    }

    protected void removeMember(UUID playerUuid) {
        members.remove(playerUuid);

        // If the leader left and there are still members, assign a new leader.
        if (leader.equals(playerUuid) && !members.isEmpty()) {
            // Get the first UUID from the set to be the new leader.
            this.leader = members.iterator().next();
        }
    }

    protected Set<Player> getOnlineMembers() {
        return members.stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .collect(Collectors.toSet());
    }

    protected UUID getLeader() {
        return leader;
    }

    public int getSize() {
        return members.size();
    }

    public boolean isMember(UUID playerUuid) {
        return members.contains(playerUuid);
    }

    public boolean isLeader(UUID playerUuid) {
        return leader.equals(playerUuid);
    }

    protected void setLeader(UUID newLeader) {
        if (members.contains(newLeader))
            this.leader = newLeader;
    }
}
