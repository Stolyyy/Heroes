package me.stolyy.heroes.utility.physics;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class WallDetection {
    private static final double STEP_SIZE = 0.0625;

    public static boolean detectWall(Location location, double radius){
        if(!isValidLocation(location) || !isFinite(radius)) {
            return false; //Input is invalid
        }

        BoundingBox box = BoundingBox.of(location, radius, radius, radius);
        World world = location.getWorld();

        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block currentBlock = world.getBlockAt(x, y, z);
                    if(currentBlock.isSolid() && !currentBlock.isPassable()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean rayCast(Location start, double distance){
        if(!isValidLocation(start) || !isFinite(distance) || distance <= 0) {
            return false; //Input is invalid
        }

        Vector direction = start.getDirection().normalize();
        Location end = start.clone().add(direction.multiply(distance));
        return rayCast(start, end);
    }

    public static boolean rayCast(Location start, Location end){
        if(!isValidLocation(start) || !isValidLocation(end)) {
            return false;
        }

        Vector v = end.toVector().subtract(start.toVector());
        double length = v.length();

        if(length <= STEP_SIZE) {
            return start.getBlock().isSolid() && !start.getBlock().isPassable();
        }

        v.normalize();
        Location current = start.clone();
        double distanceTraveled = 0;
        while (distanceTraveled < length) {
            current.add(v.clone().multiply(STEP_SIZE));
            distanceTraveled += STEP_SIZE;

            Block block = current.getBlock();
            if (block.isSolid() && !block.isPassable()) {
                return true;
            }
        }
        return end.getBlock().isSolid() && !end.getBlock().isPassable();
    }

    private static boolean isValidLocation(Location location) {
        return location != null && location.getWorld() != null &&
                isFinite(location.getX()) && isFinite(location.getY()) && isFinite(location.getZ());
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}