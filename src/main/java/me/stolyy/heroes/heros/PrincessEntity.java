package me.stolyy.heroes.heros;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class PrincessEntity {
    private final Plugin plugin;
    private final Player owner;
    private Skeleton entity;
    private int attackCooldown = 0;
    private Location targetLocation;

    public PrincessEntity(Plugin plugin, Location loc, Player owner) {
        this.plugin = plugin;
        this.owner = owner;
        this.entity = loc.getWorld().spawn(loc, Skeleton.class, skeleton -> {
            skeleton.setCustomName("Princess");
            skeleton.setCustomNameVisible(true);
            skeleton.setInvulnerable(true);
            skeleton.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3);
            skeleton.setFireTicks(0);
            skeleton.setShouldBurnInDay(false);
            skeleton.setTarget(null);
            skeleton.setCanPickupItems(false);
            skeleton.setAggressive(false);
            skeleton.getEquipment().setItemInMainHand(null);  // Remove bow
        });

        startAI();
    }

    private void startAI() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || !entity.isValid()) {
                    this.cancel();
                    return;
                }

                if (attackCooldown > 0) {
                    attackCooldown--;
                }

                Player nearestPlayer = findNearestEnemyPlayer();
                if (nearestPlayer != null) {
                    entity.getPathfinder().moveTo(nearestPlayer);
                    if (entity.getLocation().distance(nearestPlayer.getLocation()) < 15 && attackCooldown == 0) {
                        sonicBoomAttack(nearestPlayer);
                    }
                }
                if (entity.getLocation().distance(owner.getLocation()) > 30) {
                    entity.teleport(owner.getLocation());
                }

                // Constantly reset target to prevent vanilla attacking
                entity.setTarget(null);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Player findNearestEnemyPlayer() {
        return entity.getWorld().getNearbyPlayers(entity.getLocation(), 60).stream()
                .filter(p -> !p.getUniqueId().equals(owner.getUniqueId()) && p.getGameMode().equals(GameMode.ADVENTURE))
                .min((p1, p2) -> Double.compare(p1.getLocation().distanceSquared(entity.getLocation()),
                        p2.getLocation().distanceSquared(entity.getLocation())))
                .orElse(null);
    }

    private void sonicBoomAttack(Player target) {
        targetLocation = target.getLocation().clone();
        entity.lookAt(target);

        new BukkitRunnable() {
            @Override
            public void run() {
                Vector direction = targetLocation.toVector().subtract(entity.getLocation().toVector()).normalize();
                for (int i = 1; i <= 15; i++) {
                    Location particleLocation = entity.getLocation().add(direction.clone().multiply(i));
                    entity.getWorld().spawnParticle(Particle.SONIC_BOOM, particleLocation, 1, 0, 0, 0, 0);
                }
                if (target.getLocation().distanceSquared(targetLocation) < 2 * 2) {
                    target.damage(5, entity);
                }
                attackCooldown = 60; // 3 seconds cooldown
            }
        }.runTaskLater(plugin, 10L); // 0.5 seconds delay
    }

    public void remove() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }
}