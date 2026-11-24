package me.stolyy.heroes.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

        if (leader.equals(playerUuid) && !members.isEmpty()) {
            this.leader = members.iterator().next();
            Player newLeaderPlayer = Bukkit.getPlayer(leader);
            String newLeaderName = newLeaderPlayer != null ? newLeaderPlayer.getName() : leader.toString();
            Component newLeaderMessage = Component.text(newLeaderName + " is the new party leader.", NamedTextColor.GOLD);
            members.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(p -> p.sendMessage(newLeaderMessage));
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

    /**
     * Members who are currently online. Gates that decide how many players a party can actually
     * put into a game must use this, not {@link #getSize()} — an offline member still occupies a
     * slot in {@code members} but will never be seated, so counting them makes the gate disagree
     * with {@link PartyManager#getPartyMembers(Player)}, which is online-only.
     */
    public int getOnlineSize() {
        return getOnlineMembers().size();
    }

    public Set<UUID> getMemberUUIDs() {
        return Collections.unmodifiableSet(members);
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
