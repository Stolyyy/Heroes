package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.strategy.DashSafety;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;

public abstract class BaseBotAI implements BotAI {
    protected final ConfigurationSection config;
    protected final Strategy strategy;
    protected LivingEntity target;

    // The entity we last issued navigation toward. Citizens' setTarget is NOT idempotent — calling
    // it every tick cancels and restarts pathfinding, zeroing velocity, so the NPC never actually
    // moves. We only re-issue when the destination changes or navigation has stopped.
    protected LivingEntity lastNavTarget;

    // Last location-navigation destination we issued (ring seeking / crystal). Location
    // navigation is as non-idempotent as entity navigation (see above), so a new destination
    // is only issued when it drifts past REISSUE_DISTANCE or the previous path finished.
    private Location lastNavDestination;
    private static final double REISSUE_DISTANCE_SQ = 4.0; // 2 blocks
    // Slack past preferred_distance where entity-target chase (Citizens' own smooth moving-
    // target tracking) hands over to ring-point location navigation.
    private static final double RING_SLACK = 3.0;
    // Inside preferred - this, the bot backpedals (velocity, facing the target); between the
    // two slacks it holds the ring. The band prevents chase/backpedal flapping at the edge.
    private static final double BACKPEDAL_SLACK = 1.5;
    // Vanilla MOVEMENT_SPEED linearity: the vanilla default attribute (0.1) walks at 4.317
    // blocks/sec, and the relationship is linear in the attribute — A/0.1 scales that pace for
    // any attribute value. There is no vanilla backward-sprint, so backpedal targets WALK pace
    // at the hero's EFFECTIVE attribute (movementSpeed x speedMultiplier, see BotController.
    // ensureCombatState) — parity with a player holding S, not the old flat 0.18 (3.6 b/s
    // regardless of hero, versus ~0.43 = 8.6 b/s at the default 0.2 attribute).
    private static final double WALK_SPEED_PER_ATTR_PER_TICK = (4.317 / 20.0) / 0.1;
    // Ring point closer than this = already in position: stand and face rather than issuing
    // a path to a spot underfoot (per-tick setTarget restarts freeze the NPC in place).
    private static final double RING_HOLD_DIST_SQ = 4.0; // 2 blocks
    // Minimum ticks between navigation (re)issues. Citizens can't path FROM the air, so an
    // airborne bot's navigation ends instantly; an ungated "re-issue when not navigating"
    // then fired setTarget EVERY tick, and each restart zeroes velocity INCLUDING GRAVITY —
    // the bot hangs stationary in midair ("flying" bots, round 2). Rate-limiting lets gravity
    // win between issues; the grounded gate in canIssueNav stops the churn entirely.
    private static final int NAV_REISSUE_MIN_TICKS = 10;
    private int lastNavIssueTick = -NAV_REISSUE_MIN_TICKS;
    // "At walking pace" — above this the bot is mid-launch (knockback, jump, ability) and
    // nav issuing must wait, or setTarget's velocity zero deletes the flight. Shared by the
    // backpedal's don't-stomp-knockback guard.
    private static final double VELOCITY_QUIET_SQ = 0.09; // 0.3 blocks/tick, squared
    // EVADE exits at panic_distance x this — hysteresis so the task can't flap at the edge.
    private static final double EVADE_EXIT_MULTIPLIER = 1.3;

    // Target stickiness
    protected int ticksOnCurrentTarget = 0;
    protected final int minTargetDuration;
    protected final double switchThreshold;
    protected final double searchRadius;

    // Citizens navigation speed multiplier. 1.0 is the NPC's walk pace; 1.3 matches the vanilla
    // sprint ratio. Bots should always read as sprinting — walking pace looks broken in a game
    // where every player sprint-jumps.
    protected final double speedModifier;

    // Tick counter and jump cooldown
    private int tickCount = 0;
    private int lastJumpTick = 0;
    private static final int JUMP_COOLDOWN_TICKS = 8;

    // Input delay: queued actions fire after this long, with the aim direction locked to
    // where the target was at queue time. CONFIGURED IN TICKS (ai.input_delay), stored in ms —
    // it used to read the raw yml number as milliseconds, so "input_delay: 2" was a 2ms delay,
    // i.e. no delay at all. Non-final: the bot's difficulty tier overrides it after construction.
    protected long inputDelay;
    private PendingAction pendingAction;

    // Task ladder: RECOVER > EVADE > FIGHT > IDLE, evaluated top-down; the winning task owns
    // the tick. RECOVER is sticky — entered on falling-over-void, held until landing — so a
    // recovery jump's upward phase can't bounce the bot back into FIGHT mid-air. EVADE holds
    // while a melee/hybrid threat sits inside panic_distance (1.3x on exit for hysteresis).
    protected BotTask task = BotTask.IDLE;
    private final MovementActions movementActions;
    private final BotRecovery recovery;
    private final BotEvade evade;

    private record PendingAction(long executeAt, Location aimLocation, Runnable action) {}

    public BaseBotAI(ConfigurationSection config, Strategy strategy) {
        this.config = config;
        this.strategy = strategy;
        this.minTargetDuration = config.getInt("ai.min_target_duration", 60);
        this.switchThreshold = config.getDouble("ai.switch_threshold", 5.0);
        this.searchRadius = config.getDouble("ai.search_radius", 15.0);
        this.inputDelay = config.getLong("ai.input_delay", 0) * 50L; // ticks -> ms
        this.speedModifier = config.getDouble("ai.speed_modifier", 1.3);
        this.movementActions = new MovementActions(config.getConfigurationSection("movement"));
        this.recovery = new BotRecovery(movementActions);
        this.evade = new BotEvade(movementActions, speedModifier);
    }

    /** Difficulty-tier override (bots.yml difficulty.N): replaces the yml ai.input_delay base. */
    public void setInputDelayTicks(int ticks) {
        this.inputDelay = ticks * 50L;
    }

    /** Minions override to false — Tether already teleports strays back to their owner. */
    protected boolean recoveryEnabled() {
        return true;
    }

    /** Minions override to false — panic-fleeing a melee hero is not minion behavior. */
    protected boolean evadeEnabled() {
        return true;
    }

    /** Minions override to false — crystal pickup eligibility is activePlayers, which minions
     *  are not in; walking to one would be pure decoration. */
    protected boolean seeksCrystals() {
        return true;
    }

    @Override
    public BotTask getTask() {
        return task;
    }

    @Override
    public void onSpawn(Hero hero, BotController controller) {}

    @Override
    public void tick(Hero hero, BotController controller) {
        tickCount++;
        ticksOnCurrentTarget++;

        // Keep the sprint flag asserted — vanilla clears it on damage/collision, and there is no
        // client behind the NPC to re-press the key. Mostly visual (particles/pose); the actual
        // pace comes from the navigation speedModifier below.
        if (controller.getEntity() instanceof org.bukkit.entity.Player p && !p.isSprinting()) {
            p.setSprinting(true);
        }

        // 1. Target maintenance FIRST — every task fights now (combat continues while
        // recovering/evading, or the opponent gets free pressure the whole way), so target
        // validation/acquisition must run before the ladder. canTarget — the SAME predicate
        // acquisition uses — not just isAlive: with the two disagreeing, a respawning player
        // was dropped here and immediately re-acquired by findTarget every tick, so bots
        // camped respawn points swinging at ghosts.
        LivingEntity self = controller.getEntity();
        if (target != null && (!target.isValid() || target.isDead()
                || self == null || !GameManager.canTarget(self, target))) {
            target = null;
            ticksOnCurrentTarget = 0;
        }
        if (target == null) {
            target = findTarget(hero, controller);
            ticksOnCurrentTarget = 0;
        } else if (ticksOnCurrentTarget >= minTargetDuration) {
            LivingEntity closer = findCloserTarget(hero, controller);
            if (closer != null) {
                target = closer;
                ticksOnCurrentTarget = 0;
            }
        }

        // 2. Task ladder — RECOVER owns MOVEMENT when active (entry checked every tick; its
        // expensive isAboveVoid is internally gated on airborne-and-falling). Combat keeps
        // running, minus the ability slots reserved as movement tools — that's recovery fuel.
        if (recoveryEnabled()) {
            if (task == BotTask.RECOVER) {
                if (recovery.shouldExit(controller)) {
                    recovery.reset();
                    task = BotTask.IDLE;
                }
            } else if (recovery.shouldEnter(controller)) {
                task = BotTask.RECOVER;
                controller.stopNavigation();
                lastNavTarget = null;
                lastNavDestination = null;
                evade.reset();
                pendingAction = null; // a queued combat action mid-fall is stale by definition
            }
            if (task == BotTask.RECOVER) {
                recovery.tick(hero, controller);
                combatTick(hero, controller, true);
                return;
            }
        }

        // 3. EVADE — a MELEE/HYBRID threat inside panic_distance (Defensive/MidRange yml
        // knob; ranged threats never trigger it, Aggressive never panics). Owns MOVEMENT:
        // flee pathing + movement tools aimed along the escape. Combat continues (ranged
        // bots keep shooting while they kite away) minus the reserved movement slots.
        if (evadeEnabled()) {
            LivingEntity threat = findPanicThreat(hero, controller);
            if (threat != null) {
                if (task != BotTask.EVADE) {
                    controller.stopNavigation();
                    lastNavTarget = null;
                    lastNavDestination = null;
                    pendingAction = null; // a queued combat action mid-panic is stale
                }
                task = BotTask.EVADE;
                updateNavFacing(controller, threat);
                if (evade.tick(hero, controller, threat)) {
                    combatTick(hero, controller, true);
                    return;
                }
                // Cornered against the void — no safe flee direction. Fall through and FIGHT:
                // standing ground beats pacing at the ledge.
            } else if (task == BotTask.EVADE) {
                evade.reset();
                controller.stopNavigation();
                lastNavDestination = null;
            }
        }

        // 4. FIGHT / IDLE
        task = target != null ? BotTask.FIGHT : BotTask.IDLE;
        if (target != null) {
            updateNavFacing(controller, target);
            performNavigation(hero, controller);
            combatTick(hero, controller, false);
            onTargetPresent(hero, controller, target);

            if (strategy != null && (tickCount - lastJumpTick) >= JUMP_COOLDOWN_TICKS) {
                boolean jumped = strategy.performMovement(hero, controller, target);
                if (jumped) lastJumpTick = tickCount;
            }
        } else {
            updateNavFacing(controller, null);
            Location crystal = seeksCrystals() ? activeCrystalLocation(controller) : null;
            if (crystal != null) {
                // A live crystal is worth walking to even with no enemy in sight.
                issueLocationNav(controller, crystal);
            } else {
                onIdle(hero, controller);
            }
        }
    }

    /**
     * One combat pulse: fire the queued action when its delay elapsed, else consider queueing
     * a new one. With {@code reserveMovementTools}, ability slots listed in this hero's
     * movement.actions are off-limits — RECOVER/EVADE own that fuel (voidcrawler must not
     * blow its Teleboom ON the enemy while trying to escape with it). The jab is always
     * allowed. Aim conflicts resolve per-action: each fire does its own lookAt at execution
     * time, so a movement tool aimed away and an ability aimed at the target can share a tick.
     */
    private void combatTick(Hero hero, BotController controller, boolean reserveMovementTools) {
        boolean acted = processPendingAction(hero, controller);
        if (!acted && pendingAction == null && target != null) {
            performCombat(hero, controller, reserveMovementTools);
        }
    }

    /**
     * FIGHT navigation (V2 §6): constant repositioning toward the ring at preferred_distance.
     * <ul>
     * <li>A live smash crystal overrides spacing — it's a free ult, go stand on it (pickup is
     *     Game's overlap check; combat keeps running on the way).</li>
     * <li>Far outside the ring (+ RING_SLACK): entity-target chase — Citizens' own moving-
     *     target tracking repaths smoothly, and its hard-facing is correct while approaching
     *     (the packet-level kiting override wins client-side regardless).</li>
     * <li>Near or inside the ring: walk to the ring point on our side of the target. The
     *     destination moves with the target, so the bot keeps adjusting instead of standing
     *     still — including walking BACKWARD out of a too-close spot while the lookAtFunction
     *     override keeps it visibly facing the target (kiting; ProtocolLib makes it visible).</li>
     * <li>Ring point hanging over the void: chase directly instead of pacing at the ledge.</li>
     * </ul>
     */
    protected void performNavigation(Hero hero, BotController controller) {
        Location crystal = seeksCrystals() ? activeCrystalLocation(controller) : null;
        if (crystal != null) {
            issueLocationNav(controller, crystal);
            return;
        }

        double preferred = strategy != null ? strategy.getPreferredDistance() : 0;
        double distance = controller.getLocation().distance(target.getLocation());
        if (preferred <= 0 || distance > preferred + RING_SLACK) {
            lastNavDestination = null;
            navigateTo(controller, target);

            // FAR CHASE, past even the strategy's far_distance: the ordinary walk-speed chase
            // closes ground too slowly on its own, so try the hero's own movement tools as a
            // gap-closer alongside it. DashSafety/JumpSafety vet the landing and canExecute
            // vets cooldown/energy/ammo; MovementActions' own commit window (plus dash
            // cooldowns) naturally rate-limits repeat fires — no timer needed here. FIGHT-only
            // by construction: RECOVER/EVADE both return out of tick() before this runs.
            double horizontalDistance = Math.hypot(
                    target.getLocation().getX() - controller.getLocation().getX(),
                    target.getLocation().getZ() - controller.getLocation().getZ());
            if (strategy != null && horizontalDistance > strategy.getFarDistance()) {
                controller.lookAt(target.getEyeLocation());
                movementActions.tryApproach(hero, controller, target);
            }
            return;
        }

        // TOO CLOSE: true backpedal (the S-key kite). Navigation CANNOT walk a player NPC
        // backward out of this spot — the destination drifts with the target, and every
        // setTarget re-issue restarts Citizens pathfinding and zeroes velocity, which looked
        // like walk particles with no displacement. So this leg drops the navigator entirely:
        // physical lookAt STICKS while not navigating (no ProtocolLib needed here), and the
        // bot is pushed straight back by velocity each tick, like a player holding S.
        if (distance < preferred - BACKPEDAL_SLACK) {
            // A wall between here and the target: holding this spot is stalling, not kiting —
            // fall through to the full Citizens chase (same shape as the ring's isAboveVoid
            // fallback below) so A* routes around the obstruction.
            if (WallDetection.rayCast(controller.getEyeLocation(), target.getEyeLocation())) {
                lastNavDestination = null;
                navigateTo(controller, target);
                return;
            }

            if (controller.isNavigating()) controller.stopNavigation();
            lastNavTarget = null;
            lastNavDestination = null;
            controller.lookAt(target.getEyeLocation());

            Vector back = controller.getLocation().toVector()
                    .subtract(target.getLocation().toVector()).setY(0);
            // Don't backpedal off a ledge, and don't stomp a knockback flight (or a jump) —
            // only push while roughly at walking pace. Otherwise stand and face.
            boolean safe = back.lengthSquared() > 0.01 && !WallDetection.isAboveVoid(
                    controller.getLocation().clone().add(back.clone().normalize().multiply(2)));
            Vector current = controller.getVelocity();
            boolean slowEnough = current.clone().setY(0).lengthSquared() < VELOCITY_QUIET_SQ;
            if (safe && slowEnough) {
                double effectiveSpeedAttr = hero.getAttributes().getMovementSpeed()
                        * hero.getAttributes().getSpeedMultiplier();
                Vector vel = back.normalize().multiply(effectiveSpeedAttr * WALK_SPEED_PER_ATTR_PER_TICK);
                vel.setY(current.getY()); // gravity untouched
                controller.setVelocity(vel);
            }
            return;
        }

        // IN THE BAND: hold at the ring point on our side of the target. Only navigate when
        // the point is meaningfully away — issuing paths to a spot underfoot re-triggered the
        // setTarget restart churn (the walking-in-place freeze).
        //
        // A wall blocking the target makes holding position pointless — chase instead so
        // Citizens' A* routes around it (same fallback shape as the isAboveVoid check below).
        if (WallDetection.rayCast(controller.getEyeLocation(), target.getEyeLocation())) {
            lastNavDestination = null;
            navigateTo(controller, target);
            return;
        }
        Location targetLoc = target.getLocation();
        Vector toBot = controller.getLocation().toVector().subtract(targetLoc.toVector()).setY(0);
        if (toBot.lengthSquared() < 0.01) toBot = new Vector(1, 0, 0);
        Location ring = targetLoc.clone().add(toBot.normalize().multiply(preferred));
        ring.setY(controller.getLocation().getY());
        if (WallDetection.isAboveVoid(ring)) {
            lastNavDestination = null;
            navigateTo(controller, target);
            return;
        }
        if (ring.distanceSquared(controller.getLocation()) > RING_HOLD_DIST_SQ) {
            issueLocationNav(controller, ring);
        } else {
            if (controller.isNavigating()) controller.stopNavigation();
            lastNavDestination = null;
            controller.lookAt(target.getEyeLocation()); // standing guard — face the threat
        }
    }

    /** Location navigation with the non-idempotence guard: re-issue only when the destination
     *  drifted past REISSUE_DISTANCE or the previous path finished — and only through
     *  canIssueNav (grounded + rate-limited; see NAV_REISSUE_MIN_TICKS). */
    private void issueLocationNav(BotController controller, Location dest) {
        if (lastNavDestination != null && controller.isNavigating()
                && lastNavDestination.distanceSquared(dest) < REISSUE_DISTANCE_SQ) {
            return;
        }
        if (!canIssueNav(controller)) return;
        controller.navigateTo(dest);
        controller.setNavigationSpeed((float) speedModifier);
        lastNavDestination = dest.clone();
        lastNavTarget = null;
        lastNavIssueTick = tickCount;
    }

    /** Nearest targetable MELEE/HYBRID enemy inside panic range (1.3x while already evading —
     *  exit hysteresis). Null when the strategy has no panic_distance configured. */
    private LivingEntity findPanicThreat(Hero hero, BotController controller) {
        double panic = strategy != null ? strategy.getPanicDistance() : 0;
        if (panic <= 0) return null;
        double range = task == BotTask.EVADE ? panic * EVADE_EXIT_MULTIPLIER : panic;
        double rangeSq = range * range;
        LivingEntity nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (LivingEntity enemy : getNearbyEnemies(hero, controller)) {
            Hero enemyHero = me.stolyy.heroes.hero.HeroManager.getHero(enemy);
            if (enemyHero == null) continue;
            me.stolyy.heroes.hero.HeroType type = enemyHero.getAttributes().getType();
            if (type != me.stolyy.heroes.hero.HeroType.MELEE
                    && type != me.stolyy.heroes.hero.HeroType.HYBRID) continue;
            double distSq = controller.getLocation().distanceSquared(enemy.getLocation());
            if (distSq <= rangeSq && distSq < nearestSq) {
                nearest = enemy;
                nearestSq = distSq;
            }
        }
        return nearest;
    }

    /** The active smash crystal's location for this bot's game, or null. */
    private Location activeCrystalLocation(BotController controller) {
        LivingEntity self = controller.getEntity();
        if (self == null) return null;
        me.stolyy.heroes.game.Game game = GameManager.getEntityGame(self);
        return game == null ? null : game.activeCrystalLocation();
    }

    // Kiting facing (V2 §7): while a focus entity exists, navigation carries a lookAtFunction
    // so viewers see the bot facing its enemy even while pathing away — Citizens rewrites the
    // rotation packets (REQUIRES ProtocolLib; silently cosmetic-only without it). Server-side
    // yaw still belongs to navigation, which is why movement tools physically lookAt first.
    private LivingEntity navFacingFocus;

    private void updateNavFacing(BotController controller, LivingEntity focus) {
        if (focus == navFacingFocus) return;
        navFacingFocus = focus;
        controller.setNavigationLookAt(focus == null ? null : focus::getEyeLocation);
    }

    /** Navigation (re)issue gate: grounded only (Citizens can't path from the air — instant
     *  completion turns "re-issue when stopped" into per-tick setTarget churn that suspends
     *  gravity), never more often than NAV_REISSUE_MIN_TICKS, and only while the bot is at
     *  walking pace. The pace gate protects fresh knockback: setTarget ZEROES the NPC's
     *  velocity, and a just-hit bot is still flagged grounded for a tick or two before the
     *  launch lifts it — a nav issue in that window silently no-sold the hit. */
    private boolean canIssueNav(BotController controller) {
        return controller.isOnGround()
                && tickCount - lastNavIssueTick >= NAV_REISSUE_MIN_TICKS
                && controller.getVelocity().lengthSquared() < VELOCITY_QUIET_SQ;
    }

    /**
     * Issues navigation toward {@code entity}, but only when the destination changed or navigation
     * has stopped — re-issuing the same target every tick restarts Citizens pathfinding and
     * freezes the NPC in place. Shared by target-chasing and owner-follow (idle) behaviour.
     */
    protected void navigateTo(BotController controller, LivingEntity entity) {
        if (entity == lastNavTarget && controller.isNavigating()) return;
        if (!canIssueNav(controller)) return;
        controller.navigateToEntity(entity);
        // After, not before: setTarget resets Citizens' per-navigation LocalParameters, so the
        // speed has to be re-applied on every (re)issue or the bot drops back to walk pace.
        controller.setNavigationSpeed((float) speedModifier);
        lastNavTarget = entity;
        lastNavIssueTick = tickCount;
    }

    /**
     * Processes a pending action if the delay has elapsed.
     * Aims the bot at the location recorded when the action was queued,
     * so abilities fire at where the target was, not where it is now.
     */
    private boolean processPendingAction(Hero hero, BotController controller) {
        if (pendingAction == null) return false;
        if (System.currentTimeMillis() < pendingAction.executeAt()) return false;

        if (pendingAction.aimLocation() != null) {
            controller.lookAt(pendingAction.aimLocation());
        }

        pendingAction.action().run();
        pendingAction = null;
        return true;
    }

    /**
     * Queues an action with input delay.
     * The aimLocation is recorded at queue time so directional abilities
     * (hitscans, projectiles) fire at where the target was when the bot
     * decided to act, not where it moved to during the delay.
     *
     * <p>ZERO delay fires synchronously with a live aim instead: the queue is drained at the
     * START of the next combat pulse, so even a 0-delay action used to carry a full tick of
     * aim staleness — enough lateral drift for a "0 delay" hitscan bot (skullfire, tier 5)
     * to miss a strafing player.
     */
    protected void queueAction(BotController controller, Location aimLocation, Runnable action) {
        if (pendingAction != null) return;
        if (inputDelay <= 0) {
            if (aimLocation != null) controller.lookAt(aimLocation);
            action.run();
            return;
        }
        pendingAction = new PendingAction(System.currentTimeMillis() + inputDelay, aimLocation, action);
    }

    /**
     * Core combat logic run every tick when a target exists and no action is pending.
     * Checks abilities by priority (smash > primary > secondary), then jabs.
     * Jabs target the closest enemy within jab range, not just the current target.
     * {@code reserveMovementTools} (RECOVER/EVADE) skips ability slots that double as this
     * hero's movement.actions — those are escape/recovery fuel, not damage.
     */
    protected void performCombat(Hero hero, BotController controller, boolean reserveMovementTools) {
        double distance = controller.getLocation().distance(target.getLocation());
        // Aim at the FEET — deliberately. Citizens' faceLocation computes the rotation from
        // the entity's FEET, but abilities fire from the EYES: the ray is parallel-shifted UP
        // by eye height, so a feet aim arrives at the victim's eye level (correct), while an
        // eye aim arrived a full head ABOVE them (tier-5 punishgers whiffing every shot).
        Location targetLoc = target.getLocation().clone();

        // Abilities: smash > primary > secondary. Smash is additionally gated by HP thresholds
        // (ult_min_target_hp_percent / ult_min_self_hp_percent) — failing that gate falls
        // through to primary/secondary/jab exactly like a wall/landing veto would.
        if (!(reserveMovementTools && movementActions.reserves("smash"))
                && shouldAttemptUlt(controller)
                && tryUseAbility(hero, controller, hero.getSmashAbility(), AbilityType.SMASH, distance, targetLoc)) return;
        if (!(reserveMovementTools && movementActions.reserves("primary"))
                && tryUseAbility(hero, controller, hero.getPrimaryAbility(), AbilityType.PRIMARY, distance, targetLoc)) return;
        if (!(reserveMovementTools && movementActions.reserves("secondary"))
                && tryUseAbility(hero, controller, hero.getSecondaryAbility(), AbilityType.SECONDARY, distance, targetLoc)) return;

        // Jab: hit closest enemy within jab range (any enemy, not just target)
        double jabRange = hero.getCombat().getRange();
        if (jabRange > 0) {
            LivingEntity jabTarget = findClosestEnemyInRange(hero, controller, jabRange);
            if (jabTarget != null) {
                Location jabAim = jabTarget.getLocation().clone(); // feet — see targetLoc note
                queueAction(controller, jabAim, () -> {
                    LivingEntity entity = controller.getEntity();
                    if (entity == null) return;
                    // Fire-time revalidation. performJab is the client-authoritative path — the
                    // bot vouches for the hit the way a player's client does — so re-check what a
                    // client would enforce: still in reach, still visible. The input delay is
                    // plenty of time for the victim to duck behind cover or leave range.
                    if (controller.getLocation().distanceSquared(jabTarget.getLocation()) > jabRange * jabRange) return;
                    if (WallDetection.rayCast(controller.getEyeLocation(), jabTarget.getEyeLocation())) return;
                    entity.swingMainHand();
                    hero.getCombat().performJab(controller, jabTarget);
                });
            }
        }
    }

    /**
     * Ult HP gating (strategy yml knobs: ult_min_target_hp_percent / ult_min_self_hp_percent,
     * both 0 = disabled). A near-dead target is often better finished with a cheap jab than an
     * ult that telegraphs the kill; a near-dead bot risks dying mid-cast — UNLESS this is its
     * last life, where there is no future ult to save by holding and nothing left to protect
     * by not casting.
     */
    private boolean shouldAttemptUlt(BotController controller) {
        if (strategy == null) return true;
        double minTarget = strategy.getUltMinTargetHpPercent();
        double minSelf = strategy.getUltMinSelfHpPercent();
        if (minTarget <= 0 && minSelf <= 0) return true;

        if (minTarget > 0 && hpFraction(target) < minTarget) return false;
        if (minSelf > 0) {
            LivingEntity self = controller.getEntity();
            if (hpFraction(self) < minSelf && !onLastLife(self)) return false;
        }
        return true;
    }

    private boolean onLastLife(LivingEntity self) {
        if (self == null) return false;
        me.stolyy.heroes.game.Game game = GameManager.getEntityGame(self);
        if (game == null) return false;
        me.stolyy.heroes.game.GamePlayer gamePlayer = game.getGamePlayer(self.getUniqueId());
        return gamePlayer != null && gamePlayer.currentLives() <= 1;
    }

    private double hpFraction(LivingEntity entity) {
        if (entity == null) return 1.0;
        var maxAttr = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : entity.getHealth();
        return max <= 0 ? 0 : entity.getHealth() / max;
    }

    private boolean tryUseAbility(Hero hero, BotController controller, Ability ability,
                                   AbilityType type, double distance, Location targetLoc) {
        if (ability == null || !ability.canExecute(hero)) return false;
        if (!strategy.shouldUseAbility(hero, type, target, distance)) return false;
        // Landing safety before the wall check below: a dash that would sail off the stage is
        // vetoed cheaply (at most a void column scan) before paying for a raycast.
        Vector towardTarget = targetLoc.toVector().subtract(controller.getLocation().toVector());
        if (!DashSafety.isLandingSafe(hero, controller, ability, towardTarget)) return false;
        // Raycast last — it's the expensive check. Melee/dash/buff abilities pierce by default
        // (Ability.piercesWalls()); only a RangedAbility configured pierces_walls: false is
        // gated on line-of-sight to the target — arcing projectiles included, deliberately: no
        // gravity exemption.
        if (!ability.piercesWalls()
                && WallDetection.rayCast(controller.getEyeLocation(), target.getEyeLocation())) {
            return false;
        }

        Runnable action = switch (type) {
            case PRIMARY -> hero::usePrimary;
            case SECONDARY -> hero::useSecondary;
            case SMASH -> hero::useSmash;
            default -> null;
        };

        if (action != null) {
            queueAction(controller, targetLoc, action);
            return true;
        }
        return false;
    }

    private LivingEntity findClosestEnemyInRange(Hero hero, BotController controller, double range) {
        Location botLoc = controller.getLocation();
        double rangeSq = range * range;
        LivingEntity closest = null;
        double closestDistSq = rangeSq + 1;

        for (LivingEntity entity : getNearbyEnemies(hero, controller)) {
            double distSq = botLoc.distanceSquared(entity.getLocation());
            if (distSq > rangeSq || distSq >= closestDistSq) continue;
            // A player's client never resolves a melee target through a wall — neither may the
            // bot. (Players' jabs are client-authoritative, so their LOS check is implicit; the
            // bot has no client and must supply it here.)
            if (WallDetection.rayCast(controller.getEyeLocation(), entity.getEyeLocation())) continue;
            closest = entity;
            closestDistSq = distSq;
        }
        return closest;
    }

    @Override
    public void onDamageTaken(Hero hero, double damage, LivingEntity source) {
        if (target == null && source != null) {
            LivingEntity self = hero.getController() != null ? hero.getController().getEntity() : null;
            if (self != null && GameManager.canTarget(self, source)) {
                target = source;
                ticksOnCurrentTarget = 0;
            }
        }
    }

    @Override
    public void cleanup() {
        target = null;
        lastNavTarget = null;
        lastNavDestination = null;
        navFacingFocus = null;
        ticksOnCurrentTarget = 0;
        pendingAction = null;
        tickCount = 0;
        lastJumpTick = 0;
        task = BotTask.IDLE;
        recovery.reset();
        evade.reset();
        movementActions.reset();
    }

    @Override
    public LivingEntity getTarget() { return target; }

    @Override
    public void setTarget(LivingEntity target) {
        this.target = target;
        this.ticksOnCurrentTarget = 0;
    }

    /** Called each tick when a target exists. Override for extra subclass behavior. */
    protected void onTargetPresent(Hero hero, BotController controller, LivingEntity target) {}

    /**
     * Called each tick when no target is found. Default behaviour stops navigation; minions
     * override this to follow their owner instead. Override for other idle behaviour.
     */
    protected void onIdle(Hero hero, BotController controller) {
        controller.stopNavigation();
        lastNavTarget = null;
    }

    protected LivingEntity findTarget(Hero hero, BotController controller) {
        List<LivingEntity> enemies = getNearbyEnemies(hero, controller);
        if (enemies.isEmpty()) return null;

        Location botLoc = controller.getLocation();
        enemies.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(botLoc)));
        return enemies.getFirst();
    }

    private LivingEntity findCloserTarget(Hero hero, BotController controller) {
        if (target == null) return null;
        double currentDist = controller.getLocation().distance(target.getLocation());

        for (LivingEntity entity : getNearbyEnemies(hero, controller)) {
            if (entity.equals(target)) continue;
            double dist = controller.getLocation().distance(entity.getLocation());
            if (dist < currentDist - switchThreshold) {
                return entity;
            }
        }
        return null;
    }

    protected List<LivingEntity> getNearbyEnemies(Hero hero, BotController controller) {
        LivingEntity self = hero.getController().getEntity();
        List<LivingEntity> allEnemies = GameManager.getEnemies(self);
        Location botLoc = controller.getLocation();
        double radiusSq = searchRadius * searchRadius;
        return allEnemies.stream()
                .filter(e -> e.getLocation().distanceSquared(botLoc) <= radiusSq)
                // Only enemies we may actually hit: getEnemies deliberately includes respawning
                // players and invincible minions, and unfiltered acquisition is what sent bots
                // chasing them. canTarget here means "no target" when everyone is respawning —
                // and the bot idles, which is exactly the wanted behavior. Same pattern as
                // NukeSeekingMissiles' target scan.
                .filter(e -> GameManager.canTarget(self, e))
                .collect(java.util.stream.Collectors.toList());
    }

    public Strategy getStrategy() { return strategy; }
}
