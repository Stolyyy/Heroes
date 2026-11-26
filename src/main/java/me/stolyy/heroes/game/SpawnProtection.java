package me.stolyy.heroes.game;

import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Post-respawn invulnerability: for a few seconds after respawning, a player takes no damage or
 * knockback — spawn points are predictable and campable. Protection ends the moment they act or
 * commit: time runs out, they wander off the spawn point, they deal damage, or they use any
 * ability. Fully lazy — the time and distance conditions are evaluated at hit time, so no
 * listeners or per-tick work exist; the aggression breaks are called from the two choke points
 * every attack and ability use already flow through (Interactions / Hero.triggerAbilityUse).
 *
 * <p>Keyed by uuid, so bot respawns are protected identically.
 */
public final class SpawnProtection {

    private SpawnProtection() {}

    private record Protection(long expiresAt, Location anchor, double breakDistanceSq) {}

    private static final Map<UUID, Protection> active = new HashMap<>();

    /** Arms protection at the entity's current (just-respawned) position. */
    public static void arm(LivingEntity entity) {
        double seconds = Configs.requireDouble(GameConfigManager.getGameConfig(),
                "spawn-protection.seconds");
        double distance = Configs.requireDouble(GameConfigManager.getGameConfig(),
                "spawn-protection.break-distance");
        active.put(entity.getUniqueId(), new Protection(
                System.currentTimeMillis() + (long) (seconds * 1000),
                entity.getLocation().clone(),
                distance * distance));
    }

    /**
     * Whether this entity is currently spawn-protected. Expiry by time or by leaving the spawn
     * radius is resolved here, on read.
     */
    public static boolean isProtected(LivingEntity entity) {
        Protection protection = active.get(entity.getUniqueId());
        if (protection == null) return false;
        if (System.currentTimeMillis() > protection.expiresAt()
                || !entity.getWorld().equals(protection.anchor().getWorld())
                || entity.getLocation().distanceSquared(protection.anchor()) > protection.breakDistanceSq()) {
            active.remove(entity.getUniqueId());
            return false;
        }
        return true;
    }

    /** Aggression ends protection: dealing damage or using any ability. */
    public static void breakFor(UUID uuid) {
        active.remove(uuid);
    }

    public static void clear() {
        active.clear();
    }
}
