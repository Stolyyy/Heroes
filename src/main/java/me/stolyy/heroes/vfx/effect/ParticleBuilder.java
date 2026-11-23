package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.Heroes;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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

    // Applies settings to the LAST added particle config
    private ParticleConfig current() {
        if (configs.isEmpty()) throw new IllegalStateException("No particle added!");
        return configs.get(configs.size() - 1);
    }

    public ParticleBuilder count(int count) {
        current().count = count;
        return this;
    }
    
    // For general speed or extra data
    public ParticleBuilder extra(double speed) {
        current().extra = speed;
        return this;
    }
    
    // Alias for extra, commonly used for speed
    public ParticleBuilder speed(double speed) {
        return extra(speed);
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

    private void spawnOne(Location loc, ParticleConfig c) {
        if (loc.getWorld() == null) return;

        // Note: no .receivers(...) — Paper defaults to players within the standard particle
        // view distance. Calling .receivers(N) forces a getNearbyEntities scan over an N-block
        // cube per particle, which is hot-path expensive and was causing server stalls when
        // hitscan trails (50+ particles per shot) fired in a populated world.
        com.destroystokyo.paper.ParticleBuilder pb = new com.destroystokyo.paper.ParticleBuilder(c.particle)
                .location(loc)
                .extra(c.extra)
                .data(c.data);

        if (c.directional) {
            pb.count(0);
            pb.offset(c.xOff, c.yOff, c.zOff);
        } else {
            pb.count(c.count);
            pb.offset(c.xOff, c.yOff, c.zOff);
        }

        pb.spawn();
    }

    // --- Public Spawn Methods ---

    public void spawn(Location location) {
        for (ParticleConfig c : configs) {
            spawnOne(location, c);
        }
    }
    
    /**
     * Spawns particle using the provided vector as direction/offset.
     * Useful for directional particles or trails where direction matters.
     */
    public void spawn(Location location, Vector direction) {
        for (ParticleConfig c : configs) {
            if (c.directional) {
                // If directional, override config offset with this runtime direction
                // Temporarily swap values? Or create copy?
                // For simplicity, we assume this call implies overriding the offset for directional logic.
                
                // Use Paper's builder directly to avoid mutating stored config
                 if (location.getWorld() == null) continue;
                 new com.destroystokyo.paper.ParticleBuilder(c.particle)
                    .location(location)
                    .count(0) // Force 0 for directional
                    .offset(direction.getX(), direction.getY(), direction.getZ())
                    .extra(c.extra)
                    .data(c.data)
                    .spawn();
            } else {
                spawnOne(location, c);
            }
        }
    }

    public void spawnRandom(Location location) {
        if (configs.isEmpty()) return;
        spawnOne(location, configs.get(random.nextInt(configs.size())));
    }
    
    // --- Utility Effects ---
    
    private static final Map<UUID, BukkitTask> BLOOD_TRAILS = new HashMap<>();

    /**
     * Blood trail on a launched victim. One task per victim (a new hit restarts it) that stops the
     * moment the victim is grounded, falling, gone, or no longer moving: a despawned entity keeps
     * reporting its last airborne location and velocity, which used to leave the trail running forever.
     */
    public static void blood(LivingEntity victim) {
        UUID uuid = victim.getUniqueId();
        BukkitTask previous = BLOOD_TRAILS.remove(uuid);
        if (previous != null) previous.cancel();

        BukkitTask task = new BukkitRunnable() {
            int g = 200;
            Location last = victim.getLocation();

            public void run() {
                Location now = victim.getLocation();
                boolean gone = !victim.isValid() || victim.isDead()
                        || (victim instanceof Player p && (!p.isOnline() || p.getGameMode() == GameMode.SPECTATOR));
                boolean settled = victim.isOnGround() || victim.getVelocity().getY() < 0.35
                        || now.getWorld() != last.getWorld() || now.distanceSquared(last) == 0;
                if (gone || settled) {
                    BLOOD_TRAILS.remove(uuid);
                    cancel();
                    return;
                }
                last = now;
                g = Math.max(g - 36, 0);
                ParticleBuilder.of(Particle.DUST_COLOR_TRANSITION)
                        .transition(Color.fromRGB(255, g, 0), Color.fromRGB(150, 0, 0), 1.2f)
                        .count(6)
                        .offset(0.2, 0.2, 0.2)
                        .spawn(now);
            }
        }.runTaskTimer(Heroes.getInstance(), 2L, 1L);
        BLOOD_TRAILS.put(uuid, task);
    }

    // --- Shapes ---

    public void line(Location start, Location end, double stepDistance) {
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize();
        
        int particleIndex = 0;
        for (double d = 0; d < distance; d += stepDistance) {
            Location loc = start.clone().add(direction.clone().multiply(d));
            if (!configs.isEmpty()) {
                spawnOne(loc, configs.get(particleIndex % configs.size()));
                particleIndex++;
            }
        }
    }

    public void ring(Location center, double radius, int points) {
        double increment = (2 * Math.PI) / points;
        int particleIndex = 0;
        
        for (int i = 0; i < points; i++) {
            double angle = i * increment;
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            Location loc = new Location(center.getWorld(), x, center.getY(), z);

            if (!configs.isEmpty()) {
                spawnOne(loc, configs.get(particleIndex % configs.size()));
                particleIndex++;
            }
        }
    }

    /**
     * A filled horizontal disc: concentric rings {@code ringSpacing} apart, each point jittered by up to
     * {@code jitter} blocks so repeated draws don't read as a static grid.
     */
    public void disc(Location center, double radius, double ringSpacing, int pointsPerBlock, double jitter) {
        Random random = new Random();
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
                if (!configs.isEmpty()) {
                    spawnOne(loc, configs.get(particleIndex % configs.size()));
                    particleIndex++;
                }
            }
        }
        if (!configs.isEmpty()) spawnOne(center, configs.get(0));
    }

    public void orientedRing(Location center, Vector direction, double radius, int points) {
        direction = direction.normalize();
        Vector v1;
        
        if (Math.abs(direction.getX()) < 0.9) {
            v1 = new Vector(0, 1, 0).crossProduct(direction).normalize();
        } else {
            v1 = new Vector(0, 0, 1).crossProduct(direction).normalize();
        }
        Vector v2 = direction.clone().crossProduct(v1).normalize();

        double increment = (2 * Math.PI) / points;
        int particleIndex = 0;

        for (int i = 0; i < points; i++) {
            double angle = i * increment;
            Vector offset = v1.clone().multiply(Math.cos(angle)).add(v2.clone().multiply(Math.sin(angle))).multiply(radius);
            Location loc = center.clone().add(offset);

            if (!configs.isEmpty()) {
                spawnOne(loc, configs.get(particleIndex % configs.size()));
                particleIndex++;
            }
        }
    }

    public void sphere(Location center, double radius, int density) {
        for (int i = 0; i < density; i++) {
            double u = Math.random();
            double v = Math.random();
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
