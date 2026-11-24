package me.stolyy.heroes.game;

import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Match combat target validation, team-side resolution, and live enemy queries. */
final class Combatants {
    private final Game game;

    Combatants(Game game) {
        this.game = game;
    }

    boolean isAlive(LivingEntity entity) {
        if (!Hitbox.isValidTarget(entity)) return false;
        if (game.gameState() != GameState.IN_PROGRESS) return false;

        if (entity instanceof Player player) {
            Team team = game.playerTeam(player.getUniqueId());
            if (team == null || team.color() == TeamColor.SPECTATOR) return false;
            if (game.isRespawning(player.getUniqueId())) return false;
        }
        return true;
    }

    boolean canTarget(LivingEntity attacker, LivingEntity victim, @Nullable Boolean overrideFriendlyFire) {
        if (attacker == null || victim == null) return false;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return false;
        if (!isAlive(victim)) return false;
        if (attacker.isDead()) return false;
        if (game.gameState() != GameState.IN_PROGRESS) return false;

        Hero victimHero = HeroManager.getHero(victim);
        if (victimHero instanceof MinionHero victimMinion && victimMinion.isInvincible()) return false;

        TeamColor attackerSide = combatColor(attacker.getUniqueId());
        TeamColor victimSide = combatColor(victim.getUniqueId());
        if (attackerSide != null && attackerSide == victimSide) {
            return overrideFriendlyFire != null
                    ? overrideFriendlyFire
                    : game.friendlyFire(attacker.getUniqueId());
        }
        return true;
    }

    @Nullable TeamColor combatColor(UUID uuid) {
        Team team = game.playerTeam(uuid);
        TeamColor color = team != null ? team.color() : null;
        if (color != TeamColor.SPECTATOR) return color;
        return game.originalColor(uuid);
    }

    List<LivingEntity> getEnemies(UUID entityUUID) {
        Team myTeam = game.playerTeam(entityUUID);
        List<LivingEntity> enemies = new ArrayList<>();

        for (UUID uuid : game.aliveIds()) {
            if (uuid.equals(entityUUID)) continue;
            Team theirTeam = game.playerTeam(uuid);
            if (theirTeam == null || theirTeam == myTeam) continue;
            Hero hero = HeroManager.getHero(uuid);
            if (hero != null && hero.getController() != null) {
                LivingEntity entity = hero.getController().getEntity();
                if (entity != null && entity.isValid()) enemies.add(entity);
            }
        }

        for (MinionHero minion : game.minions()) {
            Controller mc = minion.getController();
            if (mc == null) continue;
            UUID uuid = mc.getUniqueId();
            if (uuid == null || uuid.equals(entityUUID)) continue;
            Team theirTeam = game.playerTeam(uuid);
            if (theirTeam == null || theirTeam == myTeam) continue;
            LivingEntity entity = mc.getEntity();
            if (entity != null && entity.isValid()) enemies.add(entity);
        }
        return enemies;
    }
}
