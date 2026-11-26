package me.stolyy.heroes.ability.kit.minions.spooderbuddy;

import me.stolyy.heroes.ability.kit.spooderman.SpooderClimb;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.ai.MinionBotAI;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class SpooderBuddyAI extends MinionBotAI {
    private SpooderClimb climbAbility;

    private Location lastCheckLocation;
    private int stuckCounter;
    private int tickCounter;

    private Location lastGroundNav;

    private final double heightThreshold;
    private final int stuckCheckInterval;
    private final double stuckMoveThreshold;
    private final int stuckCounterThreshold;

    public SpooderBuddyAI(ConfigurationSection config) {
        super(config);
        this.heightThreshold = Configs.requireDouble(config, "ai.climb_height_threshold");
        this.stuckCheckInterval = Configs.requireInt(config, "ai.stuck_check_interval");
        this.stuckMoveThreshold = Configs.requireDouble(config, "ai.stuck_move_threshold");
        this.stuckCounterThreshold = Configs.requireInt(config, "ai.stuck_counter_threshold");
    }

    @Override
    public void onSpawn(Hero hero, BotController controller) {
        hero.findAbility(SpooderClimb.class).ifPresent(climb -> this.climbAbility = climb);
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
            boolean nearWall = WallDetection.isAdjacentToSolidWall(botLoc);

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

        controller.setSneaking(shouldClimb);
    }

    @Override
    protected void onIdle(Hero hero, BotController controller) {
        controller.setSneaking(false);
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
