package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.HeroType;
import me.stolyy.heroes.util.BotConfig;
import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default bot AI orchestrating targeting, task priority ({@code RECOVER > EVADE > FIGHT > IDLE}),
 * {@link BotNavigation}, and {@link BotCombat}.
 */
public class HeroBotAI implements BotAI {
    protected final ConfigurationSection config;
    protected final Strategy strategy;
    protected LivingEntity target;

    private int ticksOnCurrentTarget;
    private final int minTargetDuration;
    private final double switchThreshold;
    private final double searchRadius;

    private int tickCount;
    private int lastJumpTick;

    protected BotTask task = BotTask.IDLE;
    private final MovementActions movementActions;
    private final BotRecovery recovery;
    private final BotEvade evade;
    private final BotNavigation navigation;
    private final BotCombat combat;

    public HeroBotAI(ConfigurationSection config, Strategy strategy) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        this.minTargetDuration = Configs.requireInt(config, "ai.min_target_duration");
        this.switchThreshold = Configs.requireDouble(config, "ai.switch_threshold");
        this.searchRadius = Configs.requireDouble(config, "ai.search_radius");
        long inputDelayMs = Configs.requireLong(config, "ai.input_delay") * Heroes.MS_PER_TICK;
        double speedModifier = Configs.requireDouble(config, "ai.speed_modifier");

        this.movementActions = new MovementActions(config.getConfigurationSection("movement"));
        this.recovery = new BotRecovery(movementActions);
        this.evade = new BotEvade(movementActions, speedModifier);
        this.navigation = new BotNavigation(strategy, movementActions, speedModifier);
        this.combat = new BotCombat(strategy, movementActions, inputDelayMs);
    }

    public void setInputDelayTicks(int ticks) {
        combat.setInputDelayTicks(ticks);
    }

    protected boolean recoveryEnabled() { return true; }
    protected boolean evadeEnabled() { return true; }
    protected boolean seeksCrystals() { return true; }

    @Override
    public void onSpawn(Hero hero, BotController controller) {}

    @Override
    public void tick(Hero hero, BotController controller) {
        tickCount++;
        ticksOnCurrentTarget++;

        controller.setSprinting(true);

        LivingEntity self = controller.getEntity();
        if (self == null) return;

        if (target != null && (!target.isValid() || target.isDead() || !GameManager.canTarget(self, target))) {
            target = null;
            ticksOnCurrentTarget = 0;
        }
        if (target == null) {
            target = findTarget(controller);
            ticksOnCurrentTarget = 0;
        } else if (ticksOnCurrentTarget >= minTargetDuration) {
            LivingEntity closer = findCloserTarget(controller);
            if (closer != null) {
                target = closer;
                ticksOnCurrentTarget = 0;
            }
        }

        if (recoveryEnabled()) {
            if (task == BotTask.RECOVER) {
                if (recovery.shouldExit(controller)) {
                    recovery.reset();
                    task = BotTask.IDLE;
                }
            } else if (recovery.shouldEnter(controller)) {
                task = BotTask.RECOVER;
                navigation.stopAndClear(controller);
                evade.reset();
                combat.clearPending();
            }
            if (task == BotTask.RECOVER) {
                recovery.tick(hero, controller);
                combat.tick(hero, controller, target, () -> getNearbyEnemies(controller), true);
                return;
            }
        }

        if (evadeEnabled()) {
            LivingEntity threat = findPanicThreat(controller);
            if (threat != null) {
                if (task != BotTask.EVADE) {
                    navigation.stopAndClear(controller);
                    combat.clearPending();
                }
                task = BotTask.EVADE;
                navigation.updateFacing(controller, threat);
                if (evade.tick(hero, controller, threat)) {
                    combat.tick(hero, controller, target, () -> getNearbyEnemies(controller), true);
                    return;
                }
            } else if (task == BotTask.EVADE) {
                evade.reset();
                navigation.stopAndClear(controller);
            }
        }

        task = target != null ? BotTask.FIGHT : BotTask.IDLE;
        if (target != null) {
            navigation.updateFacing(controller, target);
            navigation.performFightNavigation(hero, controller, target, tickCount, seeksCrystals());
            combat.tick(hero, controller, target, () -> getNearbyEnemies(controller), false);
            onTargetPresent(hero, controller, target);

            if ((tickCount - lastJumpTick) >= BotConfig.jumpCooldownTicks()) {
                if (strategy.performMovement(hero, controller, target)) {
                    lastJumpTick = tickCount;
                }
            }
        } else {
            navigation.updateFacing(controller, null);
            Location crystal = seeksCrystals() ? navigation.activeCrystalLocation(controller) : null;
            if (crystal != null) {
                navigation.issueLocationNav(controller, crystal, tickCount);
            } else {
                onIdle(hero, controller);
            }
        }
    }

    private LivingEntity findPanicThreat(BotController controller) {
        double panic = strategy.getPanicDistance();
        if (panic <= 0) return null;
        double range = task == BotTask.EVADE ? panic * BotConfig.evadeExitMultiplier() : panic;
        double rangeSq = range * range;
        LivingEntity nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (LivingEntity enemy : getNearbyEnemies(controller)) {
            Hero enemyHero = HeroManager.getHero(enemy);
            if (enemyHero == null) continue;
            HeroType type = enemyHero.getAttributes().getType();
            if (type != HeroType.MELEE && type != HeroType.HYBRID) continue;
            double distSq = controller.getLocation().distanceSquared(enemy.getLocation());
            if (distSq <= rangeSq && distSq < nearestSq) {
                nearest = enemy;
                nearestSq = distSq;
            }
        }
        return nearest;
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
        ticksOnCurrentTarget = 0;
        tickCount = 0;
        lastJumpTick = 0;
        task = BotTask.IDLE;
        navigation.reset();
        combat.reset();
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

    protected void navigateToEntity(BotController controller, LivingEntity entity) {
        navigation.navigateToEntity(controller, entity, tickCount);
    }

    protected void stopNavigation(BotController controller) {
        navigation.stopAndClear(controller);
    }

    protected void onTargetPresent(Hero hero, BotController controller, LivingEntity target) {}

    protected void onIdle(Hero hero, BotController controller) {
        navigation.stopAndClear(controller);
    }

    protected LivingEntity findTarget(BotController controller) {
        List<LivingEntity> enemies = getNearbyEnemies(controller);
        if (enemies.isEmpty()) return null;

        Location botLoc = controller.getLocation();
        enemies.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(botLoc)));
        return enemies.getFirst();
    }

    private LivingEntity findCloserTarget(BotController controller) {
        if (target == null) return null;
        double currentDist = controller.getLocation().distance(target.getLocation());

        for (LivingEntity entity : getNearbyEnemies(controller)) {
            if (entity.equals(target)) continue;
            double dist = controller.getLocation().distance(entity.getLocation());
            if (dist < currentDist - switchThreshold) {
                return entity;
            }
        }
        return null;
    }

    protected List<LivingEntity> getNearbyEnemies(BotController controller) {
        LivingEntity self = controller.getEntity();
        List<LivingEntity> allEnemies = GameManager.getEnemies(self);
        Location botLoc = controller.getLocation();
        double radiusSq = searchRadius * searchRadius;
        return allEnemies.stream()
                .filter(e -> e.getLocation().distanceSquared(botLoc) <= radiusSq)
                .filter(e -> GameManager.canTarget(self, e))
                .collect(Collectors.toList());
    }
}
