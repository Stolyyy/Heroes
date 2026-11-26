package me.stolyy.heroes.physics;

import org.bukkit.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

public class VectorUtil {
    /**
     * Randomly deviates a direction vector by up to maxAngleDegrees.
     * Uniform random distribution within a cone.
     */
    public static Vector randomSpread(Vector direction, double maxAngleDegrees) {
        if (maxAngleDegrees <= 0) return direction;

        double angle = ThreadLocalRandom.current().nextDouble() * Math.toRadians(maxAngleDegrees);
        double rotation = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;

        Vector perpendicular;
        if (Math.abs(direction.getY()) > 0.99) {
            perpendicular = direction.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        } else {
            perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        }

        return direction.clone()
            .rotateAroundAxis(perpendicular, angle)
            .rotateAroundAxis(direction, rotation);
    }
}
