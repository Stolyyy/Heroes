package me.stolyy.heroes.listener;

import io.papermc.paper.event.player.PlayerPickItemEvent;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameEffects;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.physics.Interactions;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {
    private static final Map<UUID, Integer> suffocationTicks = new HashMap<>();

    // Mirrors Interactions.ZERO_HP_BUFFER: the alive-at-displayed-0 HP floor bots must be parked
    // at instead of vanilla's setHealth(0), which Citizens tears the NPC down for permanently.
    private static final double ZERO_HP_BUFFER = 0.5;

    @EventHandler
    public void onItemPickup(PlayerPickItemEvent event){
        if(GameManager.isPlayerInGame(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        // Don't block teleports — PlayerTeleportEvent extends PlayerMoveEvent
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
                    // Keep spectators inside the arena without killing them, and in SPECTATOR mode —
                    // the teleport alone would leave them adventure-bound and visibly solid.
                    p.teleport(game.settings().map().spectatorLocation());
                    p.setGameMode(GameMode.SPECTATOR);
                } else {
                    // Void damage = the HP the fall still had left to take off them, read BEFORE
                    // setHealth(0) throws it away. Reading it after is why every void-damage row on
                    // disk is 0 — and Interactions.recordVoidDamage had no production caller at all.
                    // Credit resolves through the game's last credited hit (a minion routes to its
                    // owner via creditedId); no credited hit means nobody to bill, so record nothing.
                    Game.DamageCredit credit = game.lastDamageCredit(p.getUniqueId());
                    if (credit != null) {
                        Interactions.recordVoidDamage(
                                Bukkit.getPlayer(game.creditedId(credit.damager())),
                                credit.ability(), p.getHealth());
                        game.tallyVoidDamage(credit.damager(), p.getUniqueId(), p.getHealth());
                    }
                    p.setHealth(0);
                    p.sendMessage("You went out of bounds!");
                }
            }
        } else if (game != null && game.gameState() == GameState.ENDED
                && game.outsideArena(e.getTo())) {
            // The game is over but players haven't been sent to the lobby yet — someone who was
            // mid-air over the void at the final kill would otherwise fall into vanilla void
            // damage. Park them at the spectator spot instead.
            p.teleport(game.settings().map().spectatorLocation());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Game game = GameManager.getPlayerGame(p);

        // Vanilla death messages broadcast to the WHOLE server, so anything game-adjacent must be
        // muted here: the kill feed in handleDeath is game-scoped, but a bot/player dying outside
        // IN_PROGRESS (void fall during the countdown or right after the end) skips the cancel
        // below and would leak "X was slain by Bot - Y" to every online player. NPC "players"
        // (bots, practice dummies, minions) never get vanilla messages under any circumstances.
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
                && event.getDismounted() instanceof org.bukkit.entity.ItemDisplay
                && GameManager.isPlayerInGame(p)) {
            event.setCancelled(true);
        }
        // Mr. Monster's grab: the victim rides the grabber, and vanilla lets any passenger
        // sneak-dismount — which would be a free instant escape. The grab itself removes the
        // victim from the registry BEFORE releasing, so its own dismount passes.
        if (event.getEntity() instanceof Player victim
                && me.stolyy.heroes.ability.kit.mrmonster.MonsterGrab.isGrabbedVictim(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Tracks the last entity to damage each victim, per-game. Used by Game.handleDeath as a fallback
     * when vanilla {@code player.getKiller()} has expired (e.g. a victim stalled above the void
     * before ultimately dying). Cleared when the victim respawns.
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        LivingEntity damager = resolveLivingDamager(event.getDamager());
        if (damager == null) return;
        if (damager.getUniqueId().equals(victim.getUniqueId())) return;

        Game victimGame = GameManager.getEntityGame(victim);
        if (victimGame == null) return;
        if (victimGame != GameManager.getEntityGame(damager)) return;
        // canTarget handles teammate friendly-fire rules, aliveness, same-game check, etc.
        if (!GameManager.canTarget(damager, victim)) return;

        victimGame.recordDamager(victim.getUniqueId(), damager.getUniqueId());
    }

    private LivingEntity resolveLivingDamager(Entity damager) {
        if (damager instanceof LivingEntity le) return le;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) return shooter;
        return null;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Global Fall Damage Disable for Heroes
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            UUID uid = player.getUniqueId();
            suffocationTicks.put(uid, suffocationTicks.getOrDefault(uid, 0) + 1);
            if (suffocationTicks.get(uid) > 19) {
                Bukkit.dispatchCommand(player, "stuck");
                suffocationTicks.put(uid, 0);
            }
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> suffocationTicks.put(uid, Math.max(suffocationTicks.getOrDefault(uid, 0) - 1, 0)), 20L);
            return;
        }

        // Environmental damage (lava, fire, fire tick, drowning, contact, hot floor, explosions,
        // ...) never routes through Interactions.applyDamage, so left alone vanilla would carry a
        // bot's real HP to 0 and Citizens' NPC death teardown would remove it permanently (see
        // Game.enforceBounds). isProcessingDamage skips this while Interactions IS mid-hit on this
        // victim (its own cosmetic victim.damage() calls fire this same event synchronously) so
        // combat/void deaths — already handled on their own paths — are never double-processed here.
        if (event.isCancelled() || Interactions.isProcessingDamage(player.getUniqueId())) return;

        Game game = GameManager.getPlayerGame(player);
        if (!isValidGame(game) || game.isRespawning(player.getUniqueId())
                || !game.isBotParticipant(player.getUniqueId())) {
            return;
        }

        if (player.getHealth() - event.getFinalDamage() <= ZERO_HP_BUFFER) {
            event.setCancelled(true);
            player.setHealth(ZERO_HP_BUFFER);
            game.handleDeath(player, null);
        }
    }

    private static boolean isValidGame(Game game){
        return game != null && game.gameState() == GameState.IN_PROGRESS;
    }
}