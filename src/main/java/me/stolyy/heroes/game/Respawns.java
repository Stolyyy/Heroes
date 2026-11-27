package me.stolyy.heroes.game;

import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.ErrorReporter;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroSelectGUI;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Spawn placement, furthest-spawn respawn calculation, and the pausable respawn countdown. */
final class Respawns {
    private final Game game;
    private final Set<UUID> reportedMissingRespawners = new HashSet<>();

    Respawns(Game game) {
        this.game = game;
    }

    void tick() {
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (!gamePlayer.respawning() || gamePlayer.respawnTicksLeft() <= 0) continue;
            // Per-entity catch prevents a single erroring respawn from wedging other participants' timers.
            try {
                Player respawner = game.resolveParticipantEntity(gamePlayer.uuid());
                if (respawner == null) {
                    if (reportedMissingRespawners.add(gamePlayer.uuid())) {
                        ErrorReporter.report(game, "Respawning participant " + gamePlayer.uuid() + " has no live entity");
                    }
                    continue;
                }
                reportedMissingRespawners.remove(gamePlayer.uuid());
                if (GUIManager.getOpenGui(respawner) instanceof HeroSelectGUI g && g.isRespawnPick()) continue;

                int left = gamePlayer.respawnTicksLeft() - 1;
                gamePlayer.setRespawnTicksLeft(left);
                if (left <= 0) {
                    gamePlayer.setRespawning(false);
                    game.visuals().showRespawnTick(respawner, 0);
                    handleRespawn(respawner);
                } else if (left % 20 == 0) {
                    game.visuals().showRespawnTick(respawner, left / 20);
                }
            } catch (Exception e) {
                ErrorReporter.report(game, "Respawn tick failed for " + gamePlayer.uuid(), e);
            }
        }
    }

    private void handleRespawn(Player player) {
        UUID uuid = player.getUniqueId();
        GamePlayer gamePlayer = game.getGamePlayer(uuid);
        gamePlayer.setRespawning(false);
        PlayerVisibility.refreshVisibility(player);
        if (game.gameState() != GameState.IN_PROGRESS) return;

        Team team = game.playerTeam(uuid);
        if (team == null || team.color() == TeamColor.SPECTATOR) return;

        // Clear prior kill credit so a fresh void fall without contact isn't attributed to the last fight.
        game.credit().clear(uuid);

        GameEffects.unRestrictPlayer(player);
        if (Interactions.isRealPlayer(player)) player.setAllowFlight(true);

        String pendingHero = gamePlayer.consumePendingHero();
        if (pendingHero != null && !pendingHero.equals(gamePlayer.currentHeroName())) {
            game.swapHero(gamePlayer, pendingHero);
        }

        GameEffects.applyEffects(player, gamePlayer.settings());
        game.onHealthChanged(player);

        Hero hero = HeroManager.getHero(player);
        hero.status().clear(hero);
        CosmeticManager.apply(hero.getController(), hero);

        PlayerVisibility.refreshGlow(game);
        game.modeConfig().onRespawn().apply(player, game);
        SpawnProtection.arm(player);
    }

    void teleportToSpawn(UUID uuid, TeamColor teamColor) {
        Objects.requireNonNull(teamColor, "teamColor must not be null");
        Player entity = game.resolveParticipantEntity(uuid);
        if (entity == null) return;

        if (teamColor == TeamColor.SPECTATOR) {
            teleportParticipant(uuid, entity, game.settings().map().spectatorLocation());
            return;
        }

        Team team = game.teamMap().get(teamColor);
        int playerIndex = rosterIndex(team, uuid);
        List<Vector> offsets = GameConfigManager.getSpawnOffsets();
        teleportParticipant(uuid, entity, game.settings().map().getSpawnForTeam(teamColor.ordinal(), playerIndex, offsets));
    }

    void teleportParticipant(UUID uuid, Player entity, Location location) {
        if (game.isBotParticipant(uuid)) {
            NpcServiceProvider.get().teleport(game.getGamePlayer(uuid).bot().getNpcHandle(), location);
        } else {
            entity.teleport(location);
        }
    }

    Location getRespawnLocation(Player player, @Nullable LivingEntity killer, Location deathLocation) {
        Team team = game.playerTeam(player.getUniqueId());
        if (team == null || team.color() == TeamColor.SPECTATOR) {
            return game.settings().map().spectatorLocation();
        }
        World world = game.settings().map().world();
        Location reference = null;
        if (killer != null && world.equals(killer.getWorld())) reference = killer.getLocation();
        else if (world.equals(deathLocation.getWorld())) reference = deathLocation;

        Location base = reference == null
                ? game.settings().map().spawnLocations().get(0).clone()
                : game.settings().map().getFurthestSpawnFromLocation(reference);
        return GameMap.applyOffset(base, rosterIndex(team, player.getUniqueId()), GameConfigManager.getSpawnOffsets());
    }

    private static int rosterIndex(Team team, UUID uuid) {
        List<UUID> roster = new ArrayList<>(team.playerUUIDs());
        roster.sort(Comparator.naturalOrder());
        return roster.indexOf(uuid);
    }
}
