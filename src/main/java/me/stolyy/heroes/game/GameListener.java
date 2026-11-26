package me.stolyy.heroes.game;

import io.papermc.paper.event.player.PlayerPickItemEvent;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.physics.Interactions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {
    private static final Map<UUID, Integer> suffocationTicks = new HashMap<>();

    @EventHandler
    public void onItemPickup(PlayerPickItemEvent event) {
        if (GameManager.isPlayerInGame(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        // PlayerTeleportEvent extends PlayerMoveEvent; ignore teleports here.
        if (e instanceof PlayerTeleportEvent) return;

        Player p = e.getPlayer();
        Game game = GameManager.getPlayerGame(p);

        if (game != null && GameEffects.isRestricted(p)) {
            Location from = e.getFrom().clone();
            Location to = e.getTo();
            from.setYaw(to.getYaw());
            from.setPitch(to.getPitch());
            e.setTo(from);
        }

        if (isValidGame(game)) {
            if (game.outsideArena(e.getTo()) && !game.isRespawning(p.getUniqueId())) {
                Team team = game.playerTeam(p.getUniqueId());
                if (team != null && team.color() == TeamColor.SPECTATOR) {
                    game.parkSpectator(p);
                } else {
                    game.killOutOfBounds(p);
                }
            }
        } else if (game != null && game.gameState() == GameState.ENDED && game.outsideArena(e.getTo())) {
            p.teleport(game.settings().map().spectatorLocation());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Game game = GameManager.getPlayerGame(p);

        if (game != null || !Interactions.isRealPlayer(p)) {
            e.deathMessage(null);
        }

        if (isValidGame(game)) {
            e.setCancelled(true);
            game.handleDeath(p, e.getPlayer().getKiller());
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p
                && event.getDismounted() instanceof ItemDisplay
                && GameManager.isPlayerInGame(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (Interactions.isProcessingDamage(victim.getUniqueId())) return;
        LivingEntity damager = resolveLivingDamager(event.getDamager());
        if (damager == null || damager.getUniqueId().equals(victim.getUniqueId())) return;

        Game victimGame = GameManager.getEntityGame(victim);
        if (victimGame == null || victimGame != GameManager.getEntityGame(damager)) return;
        if (!GameManager.canTarget(damager, victim)) return;

        victimGame.credit().recordFallbackDamager(victim.getUniqueId(), damager.getUniqueId());
    }

    private LivingEntity resolveLivingDamager(Entity damager) {
        if (damager instanceof LivingEntity le) return le;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) return shooter;
        return null;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            UUID uid = player.getUniqueId();
            suffocationTicks.put(uid, suffocationTicks.getOrDefault(uid, 0) + 1);
            if (suffocationTicks.get(uid) > 19) {
                GameManager.unstuck(player);
                suffocationTicks.put(uid, 0);
            }
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(),
                    () -> suffocationTicks.put(uid, Math.max(suffocationTicks.getOrDefault(uid, 0) - 1, 0)), 20L);
            return;
        }

        if (event.isCancelled() || Interactions.isProcessingDamage(player.getUniqueId())) return;

        Game game = GameManager.getPlayerGame(player);
        if (isValidGame(game)) {
            game.handleEnvironmentalDamage(player, event);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        suffocationTicks.remove(event.getPlayer().getUniqueId());
    }

    public static void clear() {
        suffocationTicks.clear();
    }

    private static boolean isValidGame(Game game) {
        return game != null && game.gameState() == GameState.IN_PROGRESS;
    }
}
