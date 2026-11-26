package me.stolyy.heroes.vfx.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleBuilder {

    // Helper class to store configuration for delayed spawning (shapes/loops)
    private static class ParticleConfig {
        Particle particle;
        int count = 1;
        double xOff = 0, yOff = 0, zOff = 0;
        double extra = 0; // Speed
        Object data = null;
        boolean directional = false; // Forces count to 0 and uses offset as direction

        ParticleConfig(Particle p) { this.particle = p; }
    }

    private final List<ParticleConfig> configs = new ArrayList<>();
    private final Random random = new Random();

    private ParticleBuilder() {}

    public static ParticleBuilder create() {
        return new ParticleBuilder();
    }

    public static ParticleBuilder of(Particle particle) {
        return create().add(particle);
    }

    public static ParticleBuilder of(Particle... particles) {
        ParticleBuilder builder = create();
        for (Particle p : particles) {
            builder.add(p);
        }
        return builder;
    }

    // --- Configuration ---

    public ParticleBuilder add(Particle particle) {
        configs.add(new ParticleConfig(particle));
        return this;
    }

    private ParticleConfig current() {
        if (configs.isEmpty()) throw new IllegalStateException("No particle added!");
        return configs.get(configs.size() - 1);
    }

    public ParticleBuilder count(int count) {
        current().count = count;
        return this;
    }

    public ParticleBuilder extra(double speed) {
        current().extra = speed;
        return this;
    }

    public ParticleBuilder offset(double x, double y, double z) {
        ParticleConfig c = current();
        c.xOff = x;
        c.yOff = y;
        c.zOff = z;
        return this;
    }

    public ParticleBuilder directional() {
        current().directional = true;
        return this;
    }

    // --- Data Types ---

    public ParticleBuilder color(Color color, float size) {
        current().data = new Particle.DustOptions(color, size);
        return this;
    }

    public ParticleBuilder transition(Color from, Color to, float size) {
        current().data = new Particle.DustTransition(from, to, size);
        return this;
    }

    public ParticleBuilder item(ItemStack item) {
        current().data = item;
        return this;
    }

    public ParticleBuilder block(BlockData block) {
        current().data = block;
        return this;
    }

    // --- Spawning Logic ---

    private void spawnOne(Location loc, ParticleConfig c, @Nullable Vector directionOverride) {
        if (loc.getWorld() == null) return;

        double xOff = c.directional && directionOverride != null ? directionOverride.getX() : c.xOff;
        double yOff = c.directional && directionOverride != null ? directionOverride.getY() : c.yOff;
        double zOff = c.directional && directionOverride != null ? directionOverride.getZ() : c.zOff;

        // Note: no .receivers(...) — Paper defaults to players within standard view distance.
        // Calling .receivers(N) forces an expensive getNearbyEntities cube scan per particle.
        new com.destroystokyo.paper.ParticleBuilder(c.particle)
                .location(loc)
                .count(c.directional ? 0 : c.count)
                .offset(xOff, yOff, zOff)
                .extra(c.extra)
                .data(c.data)
                .spawn();
    }

    private void spawnCycled(Location loc, int index) {
        if (!configs.isEmpty()) {
            spawnOne(loc, configs.get(index % configs.size()), null);
        }
    }

    // --- Public Spawn Methods ---

    public void spawn(Location location) {
        for (ParticleConfig c : configs) {
            spawnOne(location, c, null);
        }
    }

    /** Spawns configured particles, overriding directional offsets with {@code direction}. */
    public void spawn(Location location, Vector direction) {
        for (ParticleConfig c : configs) {
            spawnOne(location, c, direction);
        }
    }

    public void spawnRandom(Location location) {
        if (configs.isEmpty()) return;
        spawnOne(location, configs.get(random.nextInt(configs.size())), null);
    }

    // --- Shapes ---

    public void line(Location start, Location end, double stepDistance) {
        if (!Double.isFinite(stepDistance) || stepDistance <= 0) {
            throw new IllegalArgumentException("stepDistance must be > 0, got " + stepDistance);
        }
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize();

        int particleIndex = 0;
        for (double d = 0; d < distance; d += stepDistance) {
            Location loc = start.clone().add(direction.clone().multiply(d));
            spawnCycled(loc, particleIndex++);
        }
    }

    public void ring(Location center, double radius, int points) {
        double increment = (2 * Math.PI) / points;
        for (int i = 0; i < points; i++) {
            double angle = i * increment;
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            spawnCycled(loc, i);
        }
    }

    /**
     * A filled horizontal disc: concentric rings {@code ringSpacing} apart, each point jittered by up to
     * {@code jitter} blocks so repeated draws don't read as a static grid.
     */
    public void disc(Location center, double radius, double ringSpacing, int pointsPerBlock, double jitter) {
        if (!Double.isFinite(ringSpacing) || ringSpacing <= 0) {
            throw new IllegalArgumentException("ringSpacing must be > 0, got " + ringSpacing);
        }
        int particleIndex = 0;
        for (double r = ringSpacing; r <= radius + 1e-9; r += ringSpacing) {
            int points = Math.max(6, (int) Math.round(2 * Math.PI * r * pointsPerBlock));
            double increment = (2 * Math.PI) / points;
            double phase = random.nextDouble() * increment;
            for (int i = 0; i < points; i++) {
                double angle = phase + i * increment;
                double x = center.getX() + r * Math.cos(angle) + (random.nextDouble() - 0.5) * jitter;
                double z = center.getZ() + r * Math.sin(angle) + (random.nextDouble() - 0.5) * jitter;
                Location loc = new Location(center.getWorld(), x, center.getY() + (random.nextDouble() - 0.5) * jitter, z);
                spawnCycled(loc, particleIndex++);
            }
        }
        spawnCycled(center, 0);
    }

    public void orientedRing(Location center, Vector direction, double radius, int points) {
        direction = direction.clone().normalize();
        Vector v1;

        if (Math.abs(direction.getX()) < 0.9) {
            v1 = new Vector(0, 1, 0).crossProduct(direction).normalize();
        } else {
            v1 = new Vector(0, 0, 1).crossProduct(direction).normalize();
        }
        Vector v2 = direction.clone().crossProduct(v1).normalize();

        double increment = (2 * Math.PI) / points;
        for (int i = 0; i < points; i++) {
            double angle = i * increment;
            Vector offset = v1.clone().multiply(Math.cos(angle)).add(v2.clone().multiply(Math.sin(angle))).multiply(radius);
            Location loc = center.clone().add(offset);
            spawnCycled(loc, i);
        }
    }

    public void sphere(Location center, double radius, int density) {
        for (int i = 0; i < density; i++) {
            double u = random.nextDouble();
            double v = random.nextDouble();
            double theta = 2 * Math.PI * u;
            double phi = Math.acos(2 * v - 1);

            double x = center.getX() + (radius * Math.sin(phi) * Math.cos(theta));
            double y = center.getY() + (radius * Math.sin(phi) * Math.sin(theta));
            double z = center.getZ() + (radius * Math.cos(phi));

            Location loc = new Location(center.getWorld(), x, y, z);
            spawnRandom(loc);
        }
    }
}
