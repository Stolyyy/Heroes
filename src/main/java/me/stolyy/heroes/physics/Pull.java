package me.stolyy.heroes.physics;

import me.stolyy.heroes.control.Controller;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Shared self-pull helper: moves a controlled hero toward a fixed target point at a caller-supplied
 * speed until within {@code arrivalDistance}.
 */
public final class Pull {
    private Pull() {}

    public static boolean toward(Controller controller, Location target, double speed, double arrivalDistance) {
        return toward(controller, target, speed, arrivalDistance, null);
    }

    /**
     * @return {@code true} once the controller is within {@code arrivalDistance} of {@code target}
     */
    public static boolean toward(Controller controller, Location target, double speed,
                                 double arrivalDistance, @Nullable Vector additiveDrift) {
        Vector diff = target.toVector().subtract(controller.getLocation().toVector());
        if (diff.length() <= Math.max(arrivalDistance, 1.0E-6)) {
            return true;
        }
        Vector velocity = diff.normalize().multiply(speed);
        if (additiveDrift != null) {
            velocity.add(additiveDrift);
        }
        controller.setVelocity(velocity);
        return false;
    }
}
