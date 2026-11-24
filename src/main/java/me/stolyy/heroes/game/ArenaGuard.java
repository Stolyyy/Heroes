package me.stolyy.heroes.game;

import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.ErrorReporter;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Enforces arena boundaries (including bots that lack move events) and frees wall-embedded players. */
final class ArenaGuard {
    private static final int TRAPPED_GRACE_TICKS = 10;
    private static final double TRAPPED_ESCAPE_DISTANCE = 3.0;
    private static final double TRAPPED_ESCAPE_STEP = 0.1;

    private final Game game;
    private final Respawns respawns;
    private final Map<UUID, Integer> trappedTicks = new HashMap<>();
    private final Set<UUID> wrongWorldBotsLogged = new HashSet<>();

    ArenaGuard(Game game, Respawns respawns) {
        this.game = game;
        this.respawns = respawns;
    }

    boolean outsideArena(Location location) {
        return !location.getWorld().equals(game.settings().map().world())
                || !game.settings().boundaries().contains(location.toVector());
    }

    void killOutOfBounds(Player entity) {
        UUID uuid = entity.getUniqueId();
        game.credit().billVoidFall(entity);

        GamePlayer gamePlayer = game.getGamePlayer(uuid);
        if (gamePlayer != null && gamePlayer.isBot()) {
            if (!entity.getWorld().equals(game.settings().map().world()) && wrongWorldBotsLogged.add(uuid)) {
                ErrorReporter.report(game, "Bot " + gamePlayer.bot().getName() + " is in world "
                        + entity.getWorld().getName() + " instead of " + game.settings().map().world().getName());
            }
            // Route bots through handleDeath directly so Citizens does not despawn the NPC via vanilla death.
            game.handleDeath(entity, null);
        } else {
            entity.setHealth(0);
            entity.sendMessage(GameConfigManager.getMessage("game.out-of-bounds"));
        }
    }

    void enforceBounds() {
        for (UUID uuid : new HashSet<>(game.aliveIds())) {
            GamePlayer gamePlayer = game.getGamePlayer(uuid);
            if (gamePlayer.respawning()) continue;
            Player entity = game.resolveParticipantEntity(uuid);
            if (entity == null) continue;
            if (!outsideArena(entity.getLocation())) continue;
            killOutOfBounds(entity);
        }
    }

    void unstickTrappedPlayers(Set<Player> players) {
        for (Player player : players) {
            UUID uuid = player.getUniqueId();
            if (game.isRespawning(uuid) || !game.aliveIds().contains(uuid) || !WallDetection.isTrapped(player)) {
                trappedTicks.remove(uuid);
                continue;
            }
            if (trappedTicks.merge(uuid, 1, Integer::sum) < TRAPPED_GRACE_TICKS) continue;

            trappedTicks.remove(uuid);
            Vector out = WallDetection.escapeOffset(player, TRAPPED_ESCAPE_DISTANCE, TRAPPED_ESCAPE_STEP);
            if (out != null) respawns.teleportParticipant(uuid, player, player.getLocation().add(out));
            else respawns.teleportParticipant(uuid, player, respawns.getRespawnLocation(player, null, player.getLocation()));
        }
    }
}
