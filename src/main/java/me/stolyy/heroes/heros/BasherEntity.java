package me.stolyy.heroes.heros;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class BasherEntity {
    private final Plugin plugin;
    private final Player owner;
    private Zombie entity;
    private int attackCooldown = 0;

    public BasherEntity(Plugin plugin, Location loc, Player owner) {
        this.plugin = plugin;
        this.owner = owner;
        this.entity = loc.getWorld().spawn(loc, Zombie.class, zombie -> {
            zombie.setCustomName("Basher");
            zombie.setCustomNameVisible(true);
            zombie.setInvulnerable(true);
            zombie.setBaby(false);
            zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.5); zombie.setFireTicks(0);
            zombie.setShouldBurnInDay(false);
            zombie.setTarget(null);
            zombie.setAggressive(false);
            zombie.setCanPickupItems(false);
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
                    if (entity.getLocation().distance(nearestPlayer.getLocation()) < 2 && attackCooldown == 0) {
                        dashAttack(nearestPlayer);
                    }
                } else if (entity.getLocation().distance(owner.getLocation()) > 35) {
                    entity.teleport(owner.getLocation());
                }

                // Constantly reset target to prevent vanilla attacking
                entity.setTarget(null);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Player findNearestEnemyPlayer() {
        return entity.getWorld().getNearbyPlayers(entity.getLocation(), 60).stream()
                .filter(p -> !p.getUniqueId().equals(owner.getUniqueId()))
                .min((p1, p2) -> Double.compare(p1.getLocation().distanceSquared(entity.getLocation()),
                        p2.getLocation().distanceSquared(entity.getLocation())))
                .orElse(null);
    }

    private void dashAttack(Player target) {
        Vector knockback = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(1.5);
        target.setVelocity(knockback);
        target.damage(5, entity);
        attackCooldown = 60; // 3 seconds cooldown
    }

    public void remove() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }
}
