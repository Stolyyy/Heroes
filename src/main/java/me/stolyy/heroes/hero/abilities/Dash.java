package me.stolyy.heroes.hero.abilities;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.data.AbilityData;
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

    public Dash(Hero hero, AbilityData data) {
        super(hero, data);
    }

    @Override
    public void onUse() {
        if (activeDashTask != null && !activeDashTask.isCancelled()) activeDashTask.cancel();

        final Set<UUID> hitPlayers = new HashSet<>();

        double speed = (double) data.getProperty("speed");
        double duration = (double) data.getProperty("duration");
        double radius = (double) data.getProperty("radius");

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
        player.playSound(player.getLocation(), data.getSound("onHit"), SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void clean(){
        if (activeDashTask != null && !activeDashTask.isCancelled()) activeDashTask.cancel();
    }
}
