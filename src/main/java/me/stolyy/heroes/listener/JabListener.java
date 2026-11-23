package me.stolyy.heroes.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.game.GameManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

public class JabListener implements Listener {

    /**
     * Fires before vanilla attack logic runs, carrying the entity the player's own client
     * raytraced and named in its attack packet. Cancelling here prevents the vanilla
     * {@code entity.player.attack.nodamage} sound, the hurt animation, and damage application —
     * all of which would otherwise fire when cancelling the later EntityDamageByEntityEvent.
     *
     * <p>{@link PrePlayerAttackEntityEvent#getAttacked()} is the target chosen against what the
     * player was actually looking at, so honouring it is exact lag compensation for free. Deriving
     * the hit here instead — as this used to, via the jab box — tested the victim's LIVE server
     * position, which sits ~3-4 ticks ahead of the one the attacker's client drew (players are
     * broadcast every 2 ticks and then interpolated over 3). At hero movement speeds that is
     * 1.3-2.2 blocks of error pointing along the victim's travel, which is why chasing someone
     * missed and backpedalling did not.
     *
     * <p>No server-side range re-check is applied on purpose. Paper already validates reach on the
     * attack packet, and a tighter check here would have to compare against that same stale
     * position — reintroducing the exact bias this path exists to remove.
     */
    @EventHandler
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        Player p = event.getPlayer();
        event.setCancelled(true);
        if (GameManager.isRespawning(p)) return;

        Hero hero = HeroManager.getHero(p);
        if (hero == null || hero.actionsLocked()) return; // grabbed or grabbing: no punching

        // A target the client resolved but that we may not damage (teammate, spectator) falls
        // through without claiming the click, so the swing below can still catch a valid enemy.
        if (event.getAttacked() instanceof LivingEntity victim && GameManager.canTarget(p, victim)) {
            hero.getCombat().performJab(hero.getController(), victim);
        }
    }

    /**
     * Every left click, whether or not the client resolved a target. This has to be
     * PlayerArmSwingEvent rather than PlayerInteractEvent: the server's swing handler fires NO
     * interact event at all when its own raytrace lands on an entity within interaction range,
     * and because {@code jab_range} (5.5) is pushed into ENTITY_INTERACTION_RANGE — above the 4.5
     * block interaction range — that silent branch was unreachable to escape, swallowing clicks
     * outright whenever the client and server rays disagreed about what was on the line.
     * PlayerArmSwingEvent is fired unconditionally at the end of the same handler.
     *
     * <p>Jab dedupes internally: the attack path above runs first and claims the tick, so a click
     * that already resolved a target does not jab twice.
     */
    @EventHandler
    public void onArmSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player p = event.getPlayer();
        if (GameManager.isRespawning(p)) return;

        Hero hero = HeroManager.getHero(p);
        if (hero != null && !hero.actionsLocked()) {
            hero.getCombat().attemptJab(hero.getController());
        }
    }
}