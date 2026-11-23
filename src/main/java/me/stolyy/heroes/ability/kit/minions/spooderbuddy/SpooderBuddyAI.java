package me.stolyy.heroes.ability.kit.minions.spooderbuddy;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.ai.MinionBotAI;
import me.stolyy.heroes.control.strategy.Aggressive;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class SpooderBuddyAI extends MinionBotAI {
    private SpooderBuddyClimb climbAbility;

    private Location lastCheckLocation;
    private int stuckCounter;
    private int tickCounter;

    private Location lastGroundNav;

    private final double heightThreshold;
    private final int stuckCheckInterval;
    private final double stuckMoveThreshold;
    private final int stuckCounterThreshold;

    public SpooderBuddyAI(ConfigurationSection config) {
        super(config, new Aggressive(config.getConfigurationSection("strategy")));
        this.heightThreshold = config.getDouble("ai.climb_height_threshold", 2.0);
        this.stuckCheckInterval = config.getInt("ai.stuck_check_interval", 20);
        this.stuckMoveThreshold = config.getDouble("ai.stuck_move_threshold", 1.0);
        this.stuckCounterThreshold = config.getInt("ai.stuck_counter_threshold", 2);
    }

    @Override
    public void onSpawn(Hero hero, BotController controller) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive instanceof SpooderBuddyClimb climb) {
                this.climbAbility = climb;
                break;
            }
        }
    }

    @Override
    public void tick(Hero hero, BotController controller) {
        updateClimbing(controller);
        super.tick(hero, controller);
    }

    private void updateClimbing(BotController controller) {
        if (climbAbility == null) return;

        tickCounter++;
        boolean shouldClimb = false;

        if (target != null) {
            Location botLoc = controller.getLocation();
            boolean nearWall = climbAbility.isNearWall(botLoc);

            double heightDiff = target.getLocation().getY() - botLoc.getY();
            if (heightDiff > heightThreshold && nearWall) {
                shouldClimb = true;
            }

            if (heightDiff > heightThreshold && !nearWall) {
                Location ground = target.getLocation().clone();
                ground.setY(botLoc.getY());
                boolean groundMoved = lastGroundNav == null
                        || lastGroundNav.distanceSquared(ground) > 2.25;
                if (groundMoved || !controller.isNavigating()) {
                    controller.navigateTo(ground);
                    lastGroundNav = ground;
                }
            }

            if (tickCounter % stuckCheckInterval == 0) {
                if (lastCheckLocation != null) {
                    double moved = botLoc.distance(lastCheckLocation);
                    if (moved < stuckMoveThreshold) {
                        stuckCounter++;
                    } else {
                        stuckCounter = 0;
                    }
                }
                lastCheckLocation = botLoc.clone();
            }

            if (stuckCounter >= stuckCounterThreshold && nearWall) {
                shouldClimb = true;
            }
        }

        LivingEntity entity = controller.getEntity();
        if (entity != null) {
            entity.setSneaking(shouldClimb);
        }
    }

    @Override
    protected void onIdle(Hero hero, BotController controller) {
        LivingEntity entity = controller.getEntity();
        if (entity != null) entity.setSneaking(false);

        super.onIdle(hero, controller);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        lastCheckLocation = null;
        lastGroundNav = null;
        stuckCounter = 0;
        tickCounter = 0;
    }
}
