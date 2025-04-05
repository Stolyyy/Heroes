package me.stolyy.heroes.heros.minions;

import me.stolyy.heroes.utility.Equipment;
import me.stolyy.heroes.utility.Interactions;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class BasherEntity {
    //Basher mob for pug's ultimate
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
            zombie.setInvisible(true);
            zombie.getEquipment().setHelmet(Equipment.customItem(11004, "Basher"));
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
                }
                if (entity.getLocation().distance(owner.getLocation()) > 35) {
                    entity.teleport(owner.getLocation());
                }

                // Constantly reset target to prevent vanilla attacking
                //entity.setTarget(null); needed ?
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

    private void dashAttack(Player target) {
        Interactions.handleInteraction(entity.getLocation(),5,3,owner,target);
        attackCooldown = 60; // 3 seconds cooldown
    }

    public void remove() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }
}
