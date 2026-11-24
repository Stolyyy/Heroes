package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Routes Bukkit bow draw/shoot/arrow-hit events to the shooter's {@link GreenHoodBow}. */
public class GreenHoodListener implements Listener {
    public static final long FULL_DRAW_MS = 1000;
    /** Blocks/tick vanilla gives a bow arrow per unit of draw force ({@code BowItem}: force * 3). */
    public static final double VANILLA_BOW_SPEED = 3.0;

    private static final Map<UUID, Long> drawStart = new HashMap<>();

    public static void initialize() {
        Bukkit.getPluginManager().registerEvents(new GreenHoodListener(), Heroes.getInstance());
    }

    public static long drawHeldMs(UUID playerId) {
        Long start = drawStart.get(playerId);
        return start == null ? 0 : System.currentTimeMillis() - start;
    }

    @EventHandler
    public void onDrawStart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (p.getInventory().getItemInMainHand().getType() != Material.BOW) return;
        // isHandRaised is still false on the event that starts the draw; ignore mid-draw re-fires.
        if (p.isHandRaised()) return;
        Hero hero = HeroManager.getHero(p);
        if (hero == null || !(hero.getPrimaryAbility() instanceof GreenHoodBow)) return;
        drawStart.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Hero hero = HeroManager.getHero(p);
        if (hero == null || !(hero.getPrimaryAbility() instanceof GreenHoodBow bow)) return;

        long heldMs = drawHeldMs(p.getUniqueId());
        drawStart.remove(p.getUniqueId());

        if (!GameManager.canUseAbility(p)
                || (hero.getSmashAbility() instanceof SkyfallVolley volley && volley.isChanneling())) {
            event.setCancelled(true);
            return;
        }

        double charge = Math.min(1.0, heldMs / (double) FULL_DRAW_MS);
        int overchargeTicks = (int) Math.max(0, (heldMs - FULL_DRAW_MS) / 50);
        double force = event.getForce();

        AbstractArrow arrow = (AbstractArrow) event.getProjectile();
        Vector aim = p.getEyeLocation().getDirection();
        Vector carried = arrow.getVelocity().subtract(aim.clone().multiply(force * VANILLA_BOW_SPEED));

        bow.handleShoot(hero, event, aim, carried, charge, overchargeTicks, force);
    }

    @EventHandler
    public void onArrowHitEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        Hero hero = HeroManager.getHero(shooter);
        if (hero == null || !(hero.getPrimaryAbility() instanceof GreenHoodBow bow)) return;
        event.setCancelled(true);
        if (!bow.ownsArrow(arrow.getUniqueId())) return;
        if (!arrow.isValid()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            arrow.remove();
            return;
        }
        if (!GameManager.canTarget(shooter, victim)) return;

        bow.handleEntityHit(hero, arrow, victim);
    }

    @EventHandler
    public void onArrowHitGround(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (event.getHitBlock() == null) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        Hero hero = HeroManager.getHero(shooter);
        if (hero == null || !(hero.getPrimaryAbility() instanceof GreenHoodBow bow)) return;
        if (!bow.ownsArrow(arrow.getUniqueId())) return;

        bow.handleGroundHit(hero, arrow);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drawStart.remove(event.getPlayer().getUniqueId());
    }

    public static void clear() {
        drawStart.clear();
    }
}
