package me.stolyy.heroes.utility.physics;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Hitbox {
    public static Set<Player> sphere(Location location, double radius) {
        return new HashSet<>(location.getWorld().getNearbyPlayers(location, radius));
    }

    public static Set<Player> cube(Location location, double length) {
        return rectangle(location, length, length, length);
    }

    public static Set<Player> rectangle(Location location, double length, double width, double height){
        double maxSide = Math.max(length, Math.max(width, height));
        Set<Player> players = new HashSet<>(location.getWorld().getNearbyPlayers(location, maxSide * 1.5));
        players.removeIf(p -> !isInRectangleSAT(p.getBoundingBox(), location, length, width, height));
        return players;
    }

    public static Set<Player> cylinder(Location location, double radius, double height) {
        double maxSide = Math.max(radius, height);
        Set<Player> players = new HashSet<>(location.getWorld().getNearbyPlayers(location, maxSide * 1.5));
        players.removeIf(p -> !isInCylinder(p.getLocation(), location, radius, height)
                && !isInCylinder(p.getEyeLocation(), location, radius, height)
                && !isInCylinder(p.getBoundingBox().getCenter().toLocation(p.getWorld()), location, radius, height));
        return players;
    }

    public static Set<Player> cone(Location location, double radius, double height) {
        double maxSide = Math.max(radius, height);
        Set<Player> players = new HashSet<>(location.getWorld().getNearbyPlayers(location, maxSide * 1.5));
        players.removeIf(p -> !isInCone(p.getLocation(), location, radius, height)
                && !isInCone(p.getEyeLocation(), location, radius, height)
                && !isInCone(p.getBoundingBox().getCenter().toLocation(p.getWorld()), location, radius, height));
        return players;
    }



    private static boolean isInRectangle(Location check, Location location, double length, double width, double height) {
        Vector toCheck = new Vector(check.getX() - location.getX(), check.getY() - location.getY(), check.getZ() - location.getZ());
        Vector centralAxis = location.getDirection().clone();
        double l = toCheck.dot(centralAxis);
        if(l < 0 || l > length) return false;

        Vector up = new Vector(0, 1, 0);
        Vector widthAxis = centralAxis.crossProduct(up);
        Vector heightAxis;

        if(widthAxis.lengthSquared() <= 0.001){
            heightAxis = centralAxis.crossProduct(new Vector(1, 0, 0)).normalize();
            widthAxis = centralAxis.crossProduct(heightAxis).normalize();
        } else{
            widthAxis = widthAxis.normalize();
            heightAxis = centralAxis.crossProduct(widthAxis).normalize();
        }

        double w = toCheck.dot(widthAxis);
        if(w > width / 2 || w < -width / 2) return false;

        double h = toCheck.dot(heightAxis);
        return h <= height / 2 && h >= -height / 2;
    }

    private static boolean isInRectangleSAT(BoundingBox box, Location location, double length, double width, double height){
        Vector boxCenter = box.getCenter();
        Vector boxHalfExtends = new Vector(box.getWidthX() / 2, box.getHeight() / 2, box.getWidthZ() / 2);
        Vector[] boxAxes = {
            new Vector(1, 0, 0),
            new Vector(0, 1, 0),
            new Vector(0, 0, 1)
        };

        Vector[] rectangleAxes = new Vector[3];
        Vector forwardDir = location.getDirection().normalize();
        Vector worldUp = new Vector(0, 1, 0);

        Vector rightDir;
        if (Math.abs(forwardDir.dot(worldUp)) > 0.999) { // Looking straight up/down
            float yawRadians = (float) Math.toRadians(location.getYaw());
            rightDir = new Vector(-Math.cos(yawRadians), 0, -Math.sin(yawRadians)).normalize();
        } else {
            rightDir = worldUp.getCrossProduct(forwardDir).normalize();
        }

        Vector upDir = forwardDir.getCrossProduct(rightDir).normalize();
        rectangleAxes[0] = forwardDir; // For length
        rectangleAxes[1] = rightDir;   // For width
        rectangleAxes[2] = upDir;      // For height

        Vector rectangleCenter = location.toVector().add(rectangleAxes[0].clone().multiply(length / 2.0));
        Vector rectangleHalfExtents = new Vector(
                length / 2.0, // Extent along obbAxes[0] (forward)
                width / 2.0,  // Extent along obbAxes[1] (right)
                height / 2.0  // Extent along obbAxes[2] (up)
        );

        Vector centers = rectangleCenter.subtract(boxCenter);

        for(int i = 0; i < 3; i++) {
            if (isSeparatedOnAxis(centers, boxAxes[i], boxAxes, boxHalfExtends, rectangleAxes, rectangleHalfExtents)) {
                return false; // Separated on box axis
            }
            if (isSeparatedOnAxis(centers, rectangleAxes[i], rectangleAxes, rectangleHalfExtents, boxAxes, boxHalfExtends)) {
                return false; // Separated on rectangle axis
            }
        }

        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                Vector crossAxis = boxAxes[i].crossProduct(rectangleAxes[j]);
                if(crossAxis.lengthSquared() < 0.001) continue; //parallel axes, skip
                if (isSeparatedOnAxis(centers, crossAxis, boxAxes, boxHalfExtends, rectangleAxes, rectangleHalfExtents)) {
                    return false; // Separated on cross product axis
                }
            }
        }
        return true;
    }

    private static boolean isSeparatedOnAxis(Vector centers, Vector axis, Vector[] boxAxes, Vector boxHalfExtends, Vector[] rectangleAxes, Vector rectangleHalfExtents) {
        double projectedCenterDistance = Math.abs(centers.dot(axis));
        double boxProjectedRadius = boxHalfExtends.getX() * Math.abs(boxAxes[0].dot(axis)) +
                                    boxHalfExtends.getY() * Math.abs(boxAxes[1].dot(axis)) +
                                    boxHalfExtends.getZ() * Math.abs(boxAxes[2].dot(axis));
        double rectangleProjectedRadius = rectangleHalfExtents.getX() * Math.abs(rectangleAxes[0].dot(axis)) +
                                          rectangleHalfExtents.getY() * Math.abs(rectangleAxes[1].dot(axis)) +
                                          rectangleHalfExtents.getZ() * Math.abs(rectangleAxes[2].dot(axis));
        return projectedCenterDistance > (boxProjectedRadius + rectangleProjectedRadius);
    }

    private static boolean isInCylinder(Location check, Location location, double radius, double height) {
        Vector toCheck = new Vector(check.getX() - location.getX(), check.getY() - location.getY(), check.getZ() - location.getZ());
        Vector centralAxis = location.getDirection().clone();
        double x = toCheck.dot(centralAxis);
        if (!(x >= 0) || !(x <= height)) {
            return false;
        }
        double y = toCheck.crossProduct(centralAxis).length();
        return y <= radius;
    }

    private static boolean isInCone(Location check, Location location, double radius, double height) {
        Vector toCheck = new Vector(check.getX() - location.getX(), check.getY() - location.getY(), check.getZ() - location.getZ());
        Vector centralAxis = location.getDirection().clone();
        double x = toCheck.dot(centralAxis);
        if (!(x >= 0) || !(x <= height)) {
            return false;
        }
        if (height <= 0.001){
            return false;
        }
        double y = toCheck.crossProduct(centralAxis).length();
        return y <= radius * x / height;
    }
}
