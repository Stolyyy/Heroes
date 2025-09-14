package me.stolyy.legacy.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.legacy.heros.HeroEnergy;
import me.stolyy.legacy.heros.abilities.Ability;
import me.stolyy.legacy.heros.abilities.data.DashData;
import me.stolyy.heroes.heros.components.UseSneak;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.heros.configs.AbilityType;
import me.stolyy.heroes.heros.configs.HeroType;
import me.stolyy.legacy.heros.minions.BasherEntity;
import me.stolyy.legacy.heros.minions.PrincessEntity;
import me.stolyy.legacy.heros.abilities.interfaces.Dash;
import me.stolyy.legacy.Particles;
import me.stolyy.heroes.utility.physics.Hitbox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class Pug extends HeroEnergy implements UseSneak, Dash {
    private BasherEntity basher;
    private PrincessEntity princess;

    private double pounceCharge;

    public Pug(Player player){
        super(player);
    }

    @Override
    public void usePrimaryAbility() {
        if(!primary.ready()) return;
        dash(player, AbilityType.PRIMARY, (DashData) primary.abilityData());
        cooldown(primary);
    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType){
        Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg() + pounceCharge / 40, primary.kb(), player, target);
        player.playSound(player.getLocation(), "melee.punch.meleehit", SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @Override
    public void useSecondaryAbility() {
        if(!secondary.ready()) return;
        cooldown(secondary);
        Location center = player.getEyeLocation();
        Set<Player> hitPlayers = new HashSet<>();
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 3.0f, 1.0f);

        new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 6;
            final double spacing = 1.0;
            @Override
            public void run() {
                if (tick >= totalTicks) {
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
                    if (!hitPlayers.contains(target)) {
                        double distance = target.getLocation().distance(player.getLocation());
                        Interactions.handleInteraction(secondary.dmg(), secondary.kb() * (1 - distance / 6), player, target);
                        hitPlayers.add(target);
                    }
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 2L, 1L);
    }

    @Override
    public void useUltimateAbility() {
        if(ultimate.inUse() || !ultimate.ready()) {
            player.sendMessage(
                    Component.text("Ultimate ability is on cooldown! ", NamedTextColor.YELLOW)
                            .append(Component.text((int) ultimate.timeUntilUse(), NamedTextColor.WHITE))
                            .append(Component.text(" seconds remaining.", NamedTextColor.YELLOW)));
            return;
        }
        ultimate.setInUse(true);

        player.playSound(player.getLocation(), "pug.intruders.activation", SoundCategory.MASTER, 5f, 1.0f);
        ultTimer();
        Location spawnLocation = player.getLocation();
        basher = new BasherEntity(Heroes.getInstance(), spawnLocation, player);
        princess = new PrincessEntity(Heroes.getInstance(), spawnLocation, player);

        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            removeMinipugs();
            cooldown(ultimate);
        },  (long) (ultimate.duration() * 20L));
    }

    private void removeMinipugs() {
        if (basher != null) {
            basher.remove();
            basher = null;
        }
        if (princess != null) {
            princess.remove();
            princess = null;
        }
    }

    @Override
    public void usePassiveSneak() {
        if (energy() >= 30) {
            subtractEnergy(30);
            pounceCharge = 0;
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 2.0f, 1.0f);
            setCanIncreaseEnergy(false);
            BukkitTask pounce = new PounceChargeTask().runTaskTimer(Heroes.getInstance(), 0L, 1L);
            activeTasks.add(pounce);
        }
    }

    private class PounceChargeTask extends BukkitRunnable {
        @Override
        public void run() {
            if (player.isSneaking() && energy() > 0) {
                pounceCharge += 1;
                subtractEnergy(1);
                updateXpBar();
            } else {
                this.cancel();
                pounce();
                setCanIncreaseEnergy(true);
            }
        }
    }

    private void pounce(){
        Vector direction = player.getEyeLocation().getDirection();
        player.setVelocity(direction.multiply(1.1 + (pounceCharge / 20)));
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> pounceCharge = 0, 20L);
    }

    @Override
    protected void stats() {
        setHeroType(HeroType.MELEE);
        setWeight(2.5);

        primary = new Ability(AbilityType.PRIMARY,  6, 2.5, 2, new DashData());
        secondary = new Ability(AbilityType.SECONDARY, 10, 5, 5.5);
        ultimate = new Ability(AbilityType.ULTIMATE, 0,0,90, 10);
        pounceCharge = 0;

        setEnergyStats(100,100,0.3,true);
        initializeEnergyUpdates();
    }
}