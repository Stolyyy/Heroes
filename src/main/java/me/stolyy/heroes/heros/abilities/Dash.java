package me.stolyy.heroes.heros.abilities;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.configs.AbilityConfig;
import me.stolyy.heroes.utility.Interactions;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Dash extends Ability {
    private BukkitTask activeDashTask;

    public Dash(Hero hero, AbilityConfig config) {
        super(hero, config);
    }

    @Override
    public void onUse() {
        if (activeDashTask != null && !activeDashTask.isCancelled()) activeDashTask.cancel();

        final Set<UUID> hitPlayers = new HashSet<>();

        double speed = (double) config.getProperty("speed");
        double duration = (double) config.getProperty("duration");
        double radius = (double) config.getProperty("radius");

        double hitboxGracePeriod = 2;

        Vector direction = player.getEyeLocation().getDirection().clone();
        player.setVelocity(direction.multiply(speed));


        this.activeDashTask = new BukkitRunnable() {
            int elapsedTicks;
            @Override
            public void run() {
                if(elapsedTicks >= duration + hitboxGracePeriod){
                    onEnd();
                    this.cancel();
                    return;
                }
                if (elapsedTicks == duration) {
                    player.setVelocity(player.getLocation().getDirection().multiply(0.01).setY(0));
                }

                BoundingBox hitbox = player.getBoundingBox().expand(radius);
                Collection<Entity> nearbyEntities = player.getWorld().getNearbyEntities(hitbox);

                for (Entity entity : nearbyEntities) {
                    if(entity instanceof Player target && Interactions.canInteract(player, target)){
                        if(hitPlayers.add(target.getUniqueId())) onHit(target, direction);
                    }
                }
                elapsedTicks++;
            }
        }.runTaskTimer(Heroes.getInstance(), 0, 1);
    }

    public void onHit(Player target, Vector direction){
        Interactions.handleInteraction(direction, damage(), knockback(), player, target);
        player.playSound(player.getLocation(), config.getSound("onHit"), SoundCategory.MASTER, 1.0f, 1.0f);
    }

    public void clean(){
        if (activeDashTask != null && !activeDashTask.isCancelled()) activeDashTask.cancel();
    }
}
