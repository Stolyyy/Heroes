package me.stolyy.heroes.game;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure membership: which UUIDs are on which color. Per-participant state
 * (settings, lives, kills, hero override) lives on {@link GamePlayer}.
 */
public class Team {
    private final Set<UUID> players = new HashSet<>();
    private final TeamColor color;

    public Team(TeamColor color) {
        this.color = color;
    }

    public void add(UUID uuid){
        players.add(uuid);
    }

    public void remove(UUID uuid){
        players.remove(uuid);
    }

    public boolean contains(UUID uuid){
        return players.contains(uuid);
    }

    public Set<Player> onlinePlayers() {
        return players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all living entities for this team, including bots (which have no Player object).
     * Falls back to Bukkit.getPlayer() for real players that may not have a Hero assigned.
     */
    public List<LivingEntity> allLivingEntities() {
        List<LivingEntity> result = new ArrayList<>();
        for (UUID uuid : players) {
            Hero hero = HeroManager.getHero(uuid);
            if (hero != null && hero.getController() != null) {
                LivingEntity entity = hero.getController().getEntity();
                if (entity != null) result.add(entity);
            } else {
                // Fallback for real players
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) result.add(p);
            }
        }
        return result;
    }

    public Set<UUID> playerUUIDs() {
        return Collections.unmodifiableSet(players);
    }

    public TeamColor color() {
        return color;
    }
}
