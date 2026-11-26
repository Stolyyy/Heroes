package me.stolyy.heroes.hero.minion;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.game.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Static registry for tracking active minions.
 */
public class MinionManager {
    private static final Set<MinionHero> activeMinions = new LinkedHashSet<>();
    private static final Map<UUID, List<MinionHero>> ownerMinions = new HashMap<>();

    /** Tracks a spawned minion in the global and per-owner registries. */
    public static void register(MinionHero minion) {
        activeMinions.add(minion);
        UUID ownerUuid = minion.getOwnerId();
        if (ownerUuid != null) {
            ownerMinions.computeIfAbsent(ownerUuid, k -> new ArrayList<>()).add(minion);
        }
    }

    /** Stops tracking a minion after it has been despawned. */
    public static void unregister(MinionHero minion) {
        activeMinions.remove(minion);
        UUID ownerUuid = minion.getOwnerId();
        if (ownerUuid != null) {
            List<MinionHero> list = ownerMinions.get(ownerUuid);
            if (list != null) {
                list.remove(minion);
                if (list.isEmpty()) ownerMinions.remove(ownerUuid);
            }
        }
    }

    /**
     * Gets all minions owned by a specific hero.
     */
    public static List<MinionHero> getMinions(Hero owner) {
        if (owner == null || owner.getController() == null) return List.of();
        return ownerMinions.getOrDefault(owner.getController().getUniqueId(), List.of());
    }

    /**
     * Despawns and removes all minions owned by a specific hero.
     */
    public static void despawnAll(Hero owner) {
        List<MinionHero> minions = new ArrayList<>(getMinions(owner));
        for (MinionHero minion : minions) {
            minion.despawn();
        }
    }

    /**
     * Despawns and removes all minions belonging to a specific game. Used by the game lifecycle
     * (end/cleanup) so minions never outlive their game as orphaned NPCs.
     */
    public static void despawnForGame(Game game) {
        if (game == null) return;
        for (MinionHero minion : new ArrayList<>(activeMinions)) {
            if (minion.getGame() == game) {
                minion.despawn();
            }
        }
    }

    /** Despawns all active minions and clears registry state on shutdown. */
    public static void clear() {
        for (MinionHero minion : new ArrayList<>(activeMinions)) {
            minion.despawn();
        }
        activeMinions.clear();
        ownerMinions.clear();
    }

    public static Set<MinionHero> getActiveMinions() {
        return Collections.unmodifiableSet(activeMinions);
    }
}
