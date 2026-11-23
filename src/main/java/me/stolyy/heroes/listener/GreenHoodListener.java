package me.stolyy.heroes.listener;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.kit.greenhood.Arrow;
import me.stolyy.heroes.ability.kit.greenhood.Ballista;
import me.stolyy.heroes.ability.kit.greenhood.PearlArrow;
import me.stolyy.heroes.ability.kit.greenhood.RopedArrow;
import me.stolyy.heroes.ability.kit.greenhood.SkyfallVolley;
import me.stolyy.heroes.ability.kit.greenhood.TripmineArrow;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Green Hood bow flow (B18.6 architecture): every Green Hood ability is a REAL vanilla
 * arrow fired through a real bow. This listener owns the three-event routing:
 * <ol>
 *   <li>{@link EntityShootBowEvent} — keep the vanilla arrow, compute charge/overcharge
 *       from draw-hold time, and hand the arrow to the selected arrow ability, which
 *       re-aims it at its own speed and PDC-tags it. The event is cancelled only when no
 *       arrow should exist at all (ineligible shooter, Skyfall channel, Ballista hitscan).
 *       Keeping vanilla's own arrow preserves what a Bukkit-launched one loses: the
 *       shooter's momentum (added by {@code shootFromRotation}, overwritten by
 *       {@code setVelocity}), the draw spread, and the crit trail on a full draw.</li>
 *   <li>{@link EntityDamageByEntityEvent} (damager = tagged arrow) — cancel vanilla
 *       damage, route to the owning ability's hit logic.</li>
 *   <li>{@link ProjectileHitEvent} (block contact) — ground routing for roped/gravity/
 *       tripmine arrows; normal/barrage arrows despawn on contact (anti-lag).
 *       Expiration is deliberately ignored (user decision).</li>
 * </ol>
 * Charge comes from our own draw timing (getForce caps at vanilla full draw, overcharge
 * needs to keep counting past it): vanilla full draw = 20 ticks; everything past that is
 * overcharge. Launch SPEED however is force-derived ({@code Arrow.launchSpeed}) so
 * trajectories match vanilla above the quick-fire floor.
 *
 * <p>Hit detection: vanilla arrow collision needs pixel-perfect aim, so every tagged arrow
 * is ALSO swept with a box hitbox each tick (prev → current location) by a 1-tick tracker.
 * The vanilla damage event stays as a backstop; whichever path handles a hit first removes
 * the arrow and the other no-ops on the dead entity.
 */
public class GreenHoodListener implements Listener {
    public static final long FULL_DRAW_MS = 1000;
    /** Blocks/tick vanilla gives a bow arrow per unit of draw force ({@code BowItem}: force * 3). */
    public static final double VANILLA_BOW_SPEED = 3.0;

    private static final NamespacedKey TYPE_KEY = new NamespacedKey(Heroes.getInstance(), "gh_arrow_type");
    private static final NamespacedKey DAMAGE_KEY = new NamespacedKey(Heroes.getInstance(), "gh_arrow_damage");
    private static final Map<UUID, Long> drawStart = new HashMap<>();

    private static final Map<UUID, TrackedArrow> trackedArrows = new HashMap<>();
    private static BukkitTask trackerTask;

    private static final class TrackedArrow {
        final AbstractArrow arrow;
        final Hero hero;
        final double hitRadius;
        Location prevLoc;

        TrackedArrow(AbstractArrow arrow, Hero hero, double hitRadius) {
            this.arrow = arrow;
            this.hero = hero;
            this.hitRadius = hitRadius;
            this.prevLoc = arrow.getLocation();
        }
    }

    // --- PDC tag helpers ---

    public static void tagArrow(AbstractArrow arrow, String type, double damage) {
        arrow.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type);
        arrow.getPersistentDataContainer().set(DAMAGE_KEY, PersistentDataType.DOUBLE, damage);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

        // Every tagged arrow joins the swept-hitbox tracker (hit_radius from the primary).
        if (arrow.getShooter() instanceof Player p
                && HeroManager.getHero(p) != null
                && HeroManager.getHero(p).getPrimaryAbility() instanceof Arrow arrowAbility) {
            double radius = "barrage".equals(type)
                    ? arrowAbility.barrageHitRadius() : arrowAbility.hitRadius();
            trackedArrows.put(arrow.getUniqueId(),
                    new TrackedArrow(arrow, HeroManager.getHero(p), radius));
            startTrackerTask();
        }
    }

    /**
     * Re-aims an arrow the bow already built: {@code direction * speed} plus {@code carried}, the
     * part of vanilla's launch velocity that is not aim (shooter momentum and draw spread, see
     * {@link #onShoot}). Tags it so the hit routing owns it from here.
     */
    public static void aim(AbstractArrow arrow, Vector direction, double speed, Vector carried,
                           String tag, double damage) {
        arrow.setVelocity(direction.clone().normalize().multiply(speed).add(carried));
        tagArrow(arrow, tag, damage);
    }

    public static String readTag(AbstractArrow arrow) {
        return arrow.getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
    }

    public static double readDamage(AbstractArrow arrow) {
        Double dmg = arrow.getPersistentDataContainer().get(DAMAGE_KEY, PersistentDataType.DOUBLE);
        return dmg != null ? dmg : 0;
    }

    /** How long the player's current bow draw has been held, in ms (0 if not drawing). */
    public static long drawHeldMs(Player player) {
        Long start = drawStart.get(player.getUniqueId());
        return start == null ? 0 : System.currentTimeMillis() - start;
    }

    // --- Event flow ---

    @EventHandler
    public void onDrawStart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (p.getInventory().getItemInMainHand().getType() != Material.BOW) return;
        // isHandRaised is still false on the event that STARTS the draw; re-fires mid-draw
        // (already raised) must not reset the overcharge timer.
        if (p.isHandRaised()) return;
        if (!(HeroManager.getHero(p) != null && HeroManager.getHero(p).getPrimaryAbility() instanceof Arrow)) return;
        drawStart.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Hero hero = HeroManager.getHero(p);
        if (hero == null || !(hero.getPrimaryAbility() instanceof Arrow arrowAbility)) return;

        long heldMs = drawHeldMs(p);
        drawStart.remove(p.getUniqueId());

        // Skyfall owns the bow while channelling: draws feed the volley's own clock, and a
        // vanilla release must never fire a normal arrow out of the hover.
        if (!isEligible(p)
                || (hero.getSmashAbility() instanceof SkyfallVolley volley && volley.isChanneling())) {
            event.setCancelled(true);
            return;
        }

        double charge = Math.min(1.0, heldMs / (double) FULL_DRAW_MS);
        int overchargeTicks = (int) Math.max(0, (heldMs - FULL_DRAW_MS) / 50);
        // Arrow owns the whole force → speed derivation, but the FLOOR and MULTIPLIER are per
        // arrow type (R7): each armed modifier hands its own pair in, so every arrow's velocity
        // is its own balance knob.
        double force = event.getForce();

        // Vanilla has already aimed its arrow: aim * force * 3, plus the draw spread, plus the
        // shooter's momentum (horizontal always, vertical only while airborne). Everything that
        // is not the aim term is kept and re-added on top of our own speed, so the arrow leaves
        // the bow the way a vanilla one does instead of ignoring how the shooter is moving.
        AbstractArrow arrow = (AbstractArrow) event.getProjectile();
        Vector aim = p.getEyeLocation().getDirection();
        Vector carried = arrow.getVelocity().subtract(aim.clone().multiply(force * VANILLA_BOW_SPEED));

        // An armed shot-modifier replaces the selected fire mode for this one shot.
        // Precedence: tripmine → pearl → ballista (first armed wins).
        TripmineArrow tripmine = findAbility(hero, TripmineArrow.class);
        if (tripmine != null && tripmine.isArmed()) {
            tripmine.fire(arrow, aim, carried,
                    arrowAbility.launchSpeed(force, tripmine.minForce(), tripmine.forceMultiplier()));
            return;
        }

        PearlArrow pearl = findAbility(hero, PearlArrow.class);
        if (pearl != null && pearl.isArmed()) {
            pearl.fire(arrow, aim, carried, pearl.launchSpeed(force));
            return;
        }

        Ballista ballista = findAbility(hero, Ballista.class);
        if (ballista != null && ballista.isArmed()) {
            event.setCancelled(true); // hitscan bolt: no arrow entity
            ballista.fire(hero, p, charge, overchargeTicks);
            return;
        }

        arrowAbility.shoot(hero, arrow, aim, carried, charge, overchargeTicks, force);
    }

    /** 1-tick sweep: box-check each tracked arrow's travel segment so near-misses connect. */
    private static void startTrackerTask() {
        if (trackerTask != null) return;
        trackerTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            if (trackedArrows.isEmpty()) return;
            Iterator<TrackedArrow> it = trackedArrows.values().iterator();
            while (it.hasNext()) {
                TrackedArrow tracked = it.next();
                AbstractArrow arrow = tracked.arrow;
                if (!arrow.isValid()) {
                    it.remove();
                    continue;
                }
                LivingEntity shooter = tracked.hero.getController().getEntity();
                if (shooter == null) {
                    it.remove();
                    continue;
                }

                Location current = arrow.getLocation();
                Vector delta = current.toVector().subtract(tracked.prevLoc.toVector());
                if (delta.lengthSquared() < 0.0001) {
                    tracked.prevLoc = current;
                    continue;
                }

                // No wall check: the vanilla arrow already resolved block collisions, so the
                // swept segment IS the actual traveled path.
                Set<LivingEntity> victims = Hitbox.box(tracked.prevLoc, delta,
                                tracked.hitRadius, tracked.hitRadius, delta.length())
                        .exclude(shooter)
                        .excludeTeammates(shooter)
                        .pierce(false)
                        .get();
                tracked.prevLoc = current;

                if (!victims.isEmpty()) {
                    handleArrowEntityHit(tracked.hero, arrow, readTag(arrow), victims.iterator().next());
                    it.remove();
                }
            }
        }, 1L, 1L);
    }

    @EventHandler
    public void onArrowHitEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        String tag = readTag(arrow);
        if (tag == null) return;

        event.setCancelled(true); // all damage goes through Interactions, not vanilla

        if (!arrow.isValid()) return; // the swept-hitbox tracker already handled this arrow
        if (!(arrow.getShooter() instanceof Player shooter)) {
            arrow.remove();
            return;
        }
        Hero hero = HeroManager.getHero(shooter);
        if (hero == null || !(event.getEntity() instanceof LivingEntity victim)) {
            arrow.remove();
            return;
        }

        // Teammates: vanilla arrow contact is not a hit — the damage stays cancelled and
        // the arrow flies on, matching the swept-box tracker's excludeTeammates behavior.
        if (!GameManager.canTarget(shooter, victim)) return;

        trackedArrows.remove(arrow.getUniqueId());
        handleArrowEntityHit(hero, arrow, tag, victim);
    }

    /** Per-type entity-hit routing, shared by the vanilla damage event and the sweep tracker. */
    private static void handleArrowEntityHit(Hero hero, AbstractArrow arrow, String tag, LivingEntity victim) {
        switch (tag) {
            case "normal", "barrage" -> {
                if (hero.getPrimaryAbility() instanceof Arrow arrowAbility) {
                    arrowAbility.onArrowHit(hero, victim, arrow);
                }
                arrow.remove();
            }
            case "roped" -> {
                RopedArrow roped = findAbility(hero, RopedArrow.class);
                if (roped != null) roped.onContact(hero, victim.getLocation());
                arrow.remove();
            }
            case "pearl" -> {
                PearlArrow pearl = findAbility(hero, PearlArrow.class);
                if (pearl != null) pearl.onVictimContact(hero, victim, arrow);
                arrow.remove();
            }
            case "tripmine" -> {
                TripmineArrow tripmine = findAbility(hero, TripmineArrow.class);
                // arrow.getLocation() is the actual contact point (feet hit vs head hit), and the
                // victim makes the mine stick to them at that offset — see TripmineArrow.plant.
                if (tripmine != null) tripmine.plant(hero, arrow.getLocation(), arrow, victim);
                else arrow.remove();
            }
            default -> arrow.remove();
        }
    }

    @EventHandler
    public void onArrowHitGround(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (event.getHitBlock() == null) return; // entity hits handled above
        String tag = readTag(arrow);
        if (tag == null) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        Hero hero = HeroManager.getHero(shooter);
        if (hero == null) return;

        switch (tag) {
            case "roped" -> {
                RopedArrow roped = findAbility(hero, RopedArrow.class);
                if (roped != null) roped.onContact(hero, arrow.getLocation());
                arrow.remove();
            }
            case "pearl" -> {
                PearlArrow pearl = findAbility(hero, PearlArrow.class);
                if (pearl != null) pearl.onGroundContact(hero, arrow);
                arrow.remove();
            }
            case "tripmine" -> {
                TripmineArrow tripmine = findAbility(hero, TripmineArrow.class);
                if (tripmine != null) tripmine.plant(hero, arrow.getLocation(), arrow);
                else arrow.remove();
            }
            // normal/barrage arrows despawn on ground contact (sticking arrows pile up = lag)
            default -> arrow.remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drawStart.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Only one shot-modifier can be armed at a time: arming any of tripmine/pearl/
     * ballista disarms the other two (each perform() calls this with itself).
     */
    public static void disarmOthers(Hero hero, Ability arming) {
        TripmineArrow tripmine = findAbility(hero, TripmineArrow.class);
        if (tripmine != null && tripmine != arming) tripmine.disarm();
        PearlArrow pearl = findAbility(hero, PearlArrow.class);
        if (pearl != null && pearl != arming) pearl.disarm();
        Ballista ballista = findAbility(hero, Ballista.class);
        if (ballista != null && ballista != arming) ballista.disarm();
    }

    private static boolean isEligible(Player p) {
        Game game = GameManager.getPlayerGame(p);
        if (game == null) {
            // Practice range (B24.1): the bow flow works outside games for practicing players.
            return PracticeManager.isPracticing(p)
                    && p.getGameMode() == org.bukkit.GameMode.ADVENTURE
                    && HeroManager.getHero(p) != null;
        }
        return game.gameState() == GameState.IN_PROGRESS
                && !game.isRespawning(p.getUniqueId())
                && p.getGameMode() == org.bukkit.GameMode.ADVENTURE;
    }

    @SuppressWarnings("unchecked")
    private static <T> T findAbility(Hero hero, Class<T> type) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (type.isInstance(passive)) return (T) passive;
        }
        if (type.isInstance(hero.getSmashAbility())) return (T) hero.getSmashAbility();
        if (type.isInstance(hero.getSecondaryAbility())) return (T) hero.getSecondaryAbility();
        if (type.isInstance(hero.getPrimaryAbility())) return (T) hero.getPrimaryAbility();
        return null;
    }
}
