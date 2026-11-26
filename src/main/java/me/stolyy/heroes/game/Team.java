package me.stolyy.heroes.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure UUID membership per team color. Per-participant state (settings, lives, kills, hero
 * override) lives on {@link GamePlayer}.
 */
public class Team {
    private final Set<UUID> players = new HashSet<>();
    private final TeamColor color;

    public Team(TeamColor color) {
        this.color = color;
    }

    public void add(UUID uuid) {
        players.add(uuid);
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public boolean contains(UUID uuid) {
        return players.contains(uuid);
    }

    public Set<Player> onlinePlayers() {
        return players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<UUID> playerUUIDs() {
        return Collections.unmodifiableSet(players);
    }

    public TeamColor color() {
        return color;
    }
}
