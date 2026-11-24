package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.Heroes;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Red-fade blood particle trail attached to a launched combat victim until settled or gone. */
public final class BloodTrail {
    private static final int INITIAL_GREEN = 200;
    private static final int GREEN_DECAY_PER_TICK = 36;
    private static final int RED_START = 255;
    private static final int RED_END = 150;
    private static final float DUST_SIZE = 1.2f;
    private static final int PARTICLE_COUNT = 6;
    private static final double PARTICLE_SPREAD = 0.2;
    private static final double MIN_UPWARD_VELOCITY = 0.35;
    private static final long START_DELAY_TICKS = 2L;
    private static final long TICK_INTERVAL = 1L;

    private static final Map<UUID, BukkitTask> ACTIVE = new HashMap<>();

    private BloodTrail() {}

    /** Starts (or restarts) a blood trail on {@code victim} that stops once landed, falling, or gone. */
    public static void start(LivingEntity victim) {
        UUID uuid = victim.getUniqueId();
        BukkitTask previous = ACTIVE.remove(uuid);
        if (previous != null) previous.cancel();

        BukkitTask task = new BukkitRunnable() {
            int green = INITIAL_GREEN;
            Location last = victim.getLocation();

            @Override
            public void run() {
                Location now = victim.getLocation();
                boolean gone = !victim.isValid() || victim.isDead()
                        || (victim instanceof Player p && (!p.isOnline() || p.getGameMode() == GameMode.SPECTATOR));
                // Stop on velocity/position freeze too: despawned entities keep reporting last airborne location.
                boolean settled = victim.isOnGround() || victim.getVelocity().getY() < MIN_UPWARD_VELOCITY
                        || now.getWorld() != last.getWorld() || now.distanceSquared(last) == 0;
                if (gone || settled) {
                    ACTIVE.remove(uuid);
                    cancel();
                    return;
                }
                last = now;
                green = Math.max(green - GREEN_DECAY_PER_TICK, 0);
                ParticleBuilder.of(Particle.DUST_COLOR_TRANSITION)
                        .transition(Color.fromRGB(RED_START, green, 0), Color.fromRGB(RED_END, 0, 0), DUST_SIZE)
                        .count(PARTICLE_COUNT)
                        .offset(PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPREAD)
                        .spawn(now);
            }
        }.runTaskTimer(Heroes.getInstance(), START_DELAY_TICKS, TICK_INTERVAL);
        ACTIVE.put(uuid, task);
    }

    /** Cancels all active blood trail tasks (used on plugin disable). */
    public static void clear() {
        for (BukkitTask task : ACTIVE.values()) {
            task.cancel();
        }
        ACTIVE.clear();
    }
}
