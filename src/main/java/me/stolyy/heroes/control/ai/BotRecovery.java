package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.StageSampler;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * RECOVER task handler: aims toward the nearest sampled stage point and spends
 * {@link MovementActions} (including airborne jumps) until solid ground lies beneath the bot.
 */
public class BotRecovery {
    private final MovementActions movementActions;

    private Location target;
    private int recheckIn;

    public BotRecovery(MovementActions movementActions) {
        this.movementActions = movementActions;
    }

    /** Off stage and falling: airborne, descending, and above void. */
    public boolean shouldEnter(BotController controller) {
        LivingEntity self = controller.getEntity();
        return self != null
                && !controller.isOnGround()
                && controller.getVelocity().getY() < 0
                && WallDetection.isAboveVoid(controller.getLocation());
    }

    /** Ends once the bot is grounded or back above solid ground. */
    public boolean shouldExit(BotController controller) {
        return controller.getEntity() == null
                || controller.isOnGround()
                || !WallDetection.isAboveVoid(controller.getLocation());
    }

    public void reset() {
        target = null;
        recheckIn = 0;
    }

    public void tick(Hero hero, BotController controller) {
        LivingEntity self = controller.getEntity();
        if (self == null) return;

        if (--recheckIn <= 0 || target == null) {
            recheckIn = BotConfig.movementActionRecheckTicks();
            target = resolveTarget(self, controller.getLocation());
        }
        if (target != null) {
            controller.lookAt(target);
        }

        if (self.isInsideVehicle()) {
            return;
        }

        movementActions.tick(hero, controller, true);
    }

    private Location resolveTarget(LivingEntity self, Location from) {
        Game game = GameManager.getEntityGame(self);
        if (game == null) return null;
        GameMap map = game.settings().map();
        Location stage = StageSampler.nearestStagePoint(map, from);
        if (stage == null) {
            throw new IllegalStateException("Map '" + map.name() + "' sampled no solid stage points for bot recovery");
        }
        return stage.clone().add(0, BotConfig.recoveryTargetHeightOffset(), 0);
    }
}
