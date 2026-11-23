package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.map.StageSampler;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * The RECOVER task's brain: pick a point back on stage, aim at it, and spend the hero's
 * {@link MovementActions} on it until the bot lands (recover-until-dead — no give-up, per
 * user decision). The action list itself is shared with EVADE; recovery only supplies the
 * aim (the stage anchor) and the entry/exit conditions.
 */
public class BotRecovery {
    private final MovementActions movementActions;

    private Location target;
    private int recheckIn;

    public BotRecovery(MovementActions movementActions) {
        this.movementActions = movementActions;
    }

    /** Off the stage and falling: airborne, descending, nothing solid below. */
    public boolean shouldEnter(BotController controller) {
        LivingEntity self = controller.getEntity();
        return self != null
                && !controller.isOnGround()
                && controller.getVelocity().getY() < 0
                && WallDetection.isAboveVoid(controller.getLocation());
    }

    /** Recovery ends the moment there is solid ground UNDER the bot — landed, or merely back
     *  over the stage mid-air. Falling the rest of the way is free; waiting for an actual
     *  landing kept bots pumping their remaining jumps over safe ground, leaving them
     *  airborne with nothing left when the fight resumed. */
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
            // Every movement tool launches along the facing — aim first, every tick, so
            // mid-flight drift keeps correcting toward the stage.
            controller.lookAt(target);
        }

        // Riding a movement tool (shoop's RTL rocket): the ride IS the recovery — never stack
        // more actions on top of it. Once the ride has carried us back over stage, cancel it
        // (RTL toggles off on a second sneak past its reactivation window) so we drop onto the
        // ledge instead of riding the full distance off the far side of the map.
        if (self.isInsideVehicle()) {
            if (!WallDetection.isAboveVoid(controller.getLocation())) {
                hero.onSneak();
            }
            return;
        }

        movementActions.tick(hero, controller, true);
    }

    /**
     * The anchor: nearest sampled stage point, aimed {@code target_height_offset} blocks
     * ABOVE it so the bot lands on the point instead of clipping the ledge lip (and
     * below-stage ability aims don't undershoot). Spawn points are the fallback for a map
     * that sampled empty.
     */
    private Location resolveTarget(LivingEntity self, Location from) {
        Game game = GameManager.getEntityGame(self);
        if (game == null) return null;
        var map = game.settings().map();
        Location stage = StageSampler.nearestStagePoint(map, from);
        if (stage == null) {
            stage = map.spawnLocations().isEmpty() ? null : map.spawnLocations().get(0);
        }
        return stage == null ? null : stage.clone().add(0, BotConfig.recoveryTargetHeightOffset(), 0);
    }
}
