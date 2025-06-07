package me.stolyy.heroes.utility.physics;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class Hitbox {
    public static Set<Player> sphere(Location location, double radius) {
        return new HashSet<>(location.getWorld().getNearbyPlayers(location, radius));
    }

    public static Set<Player> cube(Location location, double length) {
        return rectangle(location, length, length, length);
    }

    public static Set<Player> rectangle(Location location, double length, double width, double height){
        Set<Player> players = new HashSet<>();
        Vector centralAxis = location.getDirection().clone();
        for (Player p : location.getWorld().getPlayers()) {
            Vector toPlayer = new Vector(p.getLocation().getX() - location.getX(), p.getLocation().getY() - location.getY(), p.getLocation().getZ() - location.getZ());
            double l = toPlayer.dot(centralAxis);
            if(l < 0 || l > length) continue;

            Vector up = new Vector(0, centralAxis.length(), 0);
            Vector widthAxis = centralAxis.crossProduct(up);
            Vector heightAxis;

            if(widthAxis.lengthSquared() <= 0.001){
                heightAxis = centralAxis.crossProduct(new Vector(1, 0, 0)).normalize();
                widthAxis = centralAxis.crossProduct(heightAxis).normalize();
            } else{
                widthAxis = widthAxis.normalize();
                heightAxis = centralAxis.crossProduct(widthAxis).normalize();
            }

            double w = toPlayer.dot(widthAxis);
            if(w > width / 2 || w < -width / 2) continue;

            double h = toPlayer.dot(heightAxis);
            if(h <= height / 2 && h >= -height / 2) {
                players.add(p);
            }
        }
        return players;
    }

    public static Set<Player> cylinder(Location location, double radius, double height) {
        Set<Player> players = new HashSet<>();
        Vector centralAxis = location.getDirection().clone();
        for (Player p : location.getWorld().getPlayers()) {
            Vector toPlayer = new Vector(p.getLocation().getX() - location.getX(), p.getLocation().getY() - location.getY(), p.getLocation().getZ() - location.getZ());
            double x = toPlayer.dot(centralAxis);
            if(x >= 0 && x <= height) {
                double y = toPlayer.crossProduct(centralAxis).length();
                if (y <= radius) {
                    players.add(p);
                }
            }
        }
        return players;
    }

    public static Set<Player> cone(Location location, double radius, double height) {
        Set<Player> players = new HashSet<>();
        Vector centralAxis = location.getDirection().clone();
        for (Player p : location.getWorld().getPlayers()) {
            Vector toPlayer = new Vector(p.getLocation().getX() - location.getX(), p.getLocation().getY() - location.getY(), p.getLocation().getZ() - location.getZ());
            double x = toPlayer.dot(centralAxis);
            if(x >= 0 && x <= height) {
                double y = toPlayer.crossProduct(centralAxis).length();
                if (y <= radius * x / height) {
                    players.add(p);
                }
            }
        }
        return players;
    }
}
