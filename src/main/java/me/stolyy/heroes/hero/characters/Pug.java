package me.stolyy.heroes.hero.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.abilities.*;
import me.stolyy.heroes.hero.abilities.minions.Basher;
import me.stolyy.heroes.hero.abilities.minions.Princess;
import me.stolyy.heroes.hero.components.UseSneak;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.effects.Particles;
import me.stolyy.heroes.utility.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class Pug extends Hero implements UseSneak {
    private double pounceCharge = 0;

    public Pug(Player player) {
        super(player);
    }

    @Override
    protected void defineAbilities() {
        abilities.put("primary", primary());
        abilities.put("secondary", secondary());
        abilities.put("ultimate", ultimate());
        abilities.put("passive", passive());
    }

    private Ability primary(){
        return new Dash(this, data.getAbilityData("primary"));
    }

    private Ability secondary(){
        return new Ability(this, data.getAbilityData("secondary")) {
            @Override
            public void onUse() {
                Location center = player.getEyeLocation().clone();
                Set<Player> hitPlayers = new HashSet<>();
                player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 3.0f, 1.0f);

                int ticks = (int) data.getProperty("ticks");
                double spacing = (double) data.getProperty("spacing");

                new BukkitRunnable() {
                    int tick = 0;

                    @Override
                    public void run() {
                        if (tick >= ticks) {
                            this.cancel();
                            return;
                        }

                        createRing(tick);
                        tick++;
                    }

                    private void createRing(int tick) {
                        Location ringCenter = center.clone().add(center.getDirection().clone().multiply(tick * spacing));
                        Particles.directionalRing(ringCenter, 0.3 + tick * 0.24, Particle.NOTE);
                        Set<Player> toHit = new LinkedHashSet<>(Hitbox.cylinder(ringCenter, center.getDirection(), 0.6 + tick * 0.24, spacing));
                        toHit.remove(player);
                        for (Player target : toHit) {
                            if (hitPlayers.add(target)) {
                                double distance = target.getLocation().distance(player.getLocation());
                                onHit(target, center.getDirection(), distance);
                            }
                        }
                    }
                }.runTaskTimer(Heroes.getInstance(), 2L, 1L);
            }

            public void onHit(Player target, Vector direction, double distance) {
                Interactions.handleInteraction(damage(), knockback() * (1 - distance / 6), player, target);
            }
        };
    }

    private Ability ultimate() {
        Hero h = this;
        return new Ability(this, data.getAbilityData("ultimate")) {
            Basher basher;
            Princess princess;

            @Override
            public void onUse() {
                basher = new Basher(h);
                princess = new Princess(h);
                Heroes.getScheduler().runTaskLater(Heroes.getInstance(), this::onEnd, (long) data.getDuration() * 20);
            }
            @Override
            public void onEnd() {
                if (basher != null) {
                    basher.remove();
                    basher = null;
                }
                if (princess != null) {
                    princess.remove();
                    princess = null;
                }
            }
        };
    }

    private Ability passive() {
        return new Ability(this, data.getAbilityData("passive")) {
            BukkitTask pounce;

            @Override
            public void onUse() {
                pounceCharge = 0;
                player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 2.0f, 1.0f);
                energy.disallowEnergyChanges();
                pounce = new PounceChargeTask().runTaskTimer(Heroes.getInstance(), 0L, 1L);
            }

            @Override
            public double energyCost(){
                return (double) data.getProperty("cost");
            }

            private class PounceChargeTask extends BukkitRunnable {
                @Override
                public void run() {
                    if (player.isSneaking() && energy.getCurrentEnergy() > 0) {
                        pounceCharge += 1;
                        energy.changeEnergy(-1);
                    } else {
                        this.cancel();
                        onEnd();
                    }
                }
            }

            @Override
            public void onEnd() {
                energy.allowEnergyChanges();
                Vector direction = player.getEyeLocation().getDirection();
                player.setVelocity(direction.multiply(1.1 + (pounceCharge / 20)));

                //TODO: make this a task, disable on clean, disable if a new pounce is started.
                Heroes.getScheduler().runTaskLater(Heroes.getInstance(), () -> pounceCharge = 0, 20L);
            }

            @Override
            public void clean(){
                if(pounce != null && !pounce.isCancelled()) pounce.cancel();
            }
        };
    }


    @Override
    public void usePassiveSneak() {
        abilities.get("passive").execute();
    }
}
