package me.stolyy.heroes.utility;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;

public class WallDetection {

    public static boolean detectWall(Location start, Location end, double radius) {
        if (!isValidLocation(start) || !isValidLocation(end) || !isFinite(radius)) {
            return false; //Input is invalid
        }

        World world = start.getWorld();
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        double step = 0.1;
        for (double d = 0; d <= distance; d += step) {
            Location checkLoc = start.clone().add(direction.clone().multiply(d));
            if (isInSolid(checkLoc, radius)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInSolid(Location location, double radius) {
        if (!isValidLocation(location) || !isFinite(radius)) {
            return false; //Invalid input
        }

        World world = location.getWorld();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        //Create a bounding box to detect blocks around a location
        BoundingBox projectileBox;
        try {
            projectileBox = BoundingBox.of(location, radius, radius, radius);
        } catch (IllegalArgumentException e) {
            //just in case bounding box doesn't work
            return false;
        }

        // check blocks nearby using the input of a radius
        int range = (int) Math.ceil(radius);

        // Check surrounding blocks
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    // Calculate the center of the current block
                    double blockCenterX = Math.floor(x) + dx + 0.5;
                    double blockCenterY = Math.floor(y) + dy + 0.5;
                    double blockCenterZ = Math.floor(z) + dz + 0.5;

                    // Check if the block center is within the projectile's radius
                    if (location.distance(new Location(world, blockCenterX, blockCenterY, blockCenterZ)) <= radius) {
                        Block block = world.getBlockAt((int)Math.floor(x) + dx, (int)Math.floor(y) + dy, (int)Math.floor(z) + dz);
                        if (block.getType().isSolid()) {
                            BoundingBox blockBox = block.getBoundingBox();
                            if (blockBox.overlaps(projectileBox)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isValidLocation(Location location) {
        return location != null && location.getWorld() != null &&
                isFinite(location.getX()) && isFinite(location.getY()) && isFinite(location.getZ());
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}