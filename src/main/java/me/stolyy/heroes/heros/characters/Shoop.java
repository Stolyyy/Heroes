package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.abilities.data.HitscanData;
import me.stolyy.heroes.heros.abilities.interfaces.PassiveSneak;
import me.stolyy.heroes.heros.abilities.data.ProjectileData;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.heros.HeroType;
import me.stolyy.heroes.utility.effects.Equipment;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.effects.Particles;
import me.stolyy.heroes.utility.effects.Sounds;
import me.stolyy.heroes.utility.physics.WallDetection;
import me.stolyy.heroes.heros.abilities.interfaces.Hitscan;
import me.stolyy.heroes.heros.abilities.interfaces.Projectile;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class Shoop extends HeroEnergy implements PassiveSneak, Projectile, Hitscan, Listener {
    private Set<Player> ultimateHits;
    private Ability passive;

    public Shoop(Player player) {
        super(player);
    }



    @Override
    public void usePrimaryAbility() {
        if(!primary.ready()) return;
        projectile(player, AbilityType.PRIMARY, (ProjectileData) primary.abilityData());
        cooldown(primary);
        Sounds.playSoundToWorld(player, "shoopdawhoop.active",  1.0f, 1.0f);
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        if (secondary.cd() < 5) {
            secondary.setCd(secondary.cd() + 1);
            updateSecondaryDisplay();
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        //player.sendMessage("Secondary Charge: " + secondary.cd);
        switch (abilityType) {
            case PRIMARY -> Interactions.handleInteraction(primary.dmg(), primary.kb(), player, target);
            case PASSIVE -> Interactions.handleInteraction(passive.dmg(), passive.kb(), player, target);
        }
        onRangedHit();
    }

    private void updateSecondaryDisplay(){
        ItemStack secondaryItem;

        if(secondary.cd() > 0){
            secondaryItem = Equipment.customItem(5, secondary.cd() + " Charge");
            secondaryItem.setAmount((int) secondary.cd());
        } else
            secondaryItem = Equipment.customItem(4, "Go hit someone with a lazor");

        player.getInventory().setItem(1, secondaryItem);
    }

    private void setSecondaryColor(Color color){
        ((HitscanData) secondary.abilityData()).setDustOptions(color, 1.0f);
    }

    @Override
    public void useSecondaryAbility() {
        Location targetLocation = player.getEyeLocation().clone();
        Vector direction = targetLocation.getDirection();
        String sound = "shoopdawhoop.chargedbeam.small";
        if (secondary.cd() == 0) return;
        else if (secondary.cd() == 1) {
            secondary.setDmg(2).setKb(1);
            setSecondaryColor(Color.YELLOW);
        } else if (secondary.cd() == 2) {
            secondary.setDmg(4).setKb(2);
            setSecondaryColor(Color.LIME);
        } else if (secondary.cd() == 3) {
            secondary.setDmg(7).setKb(3);
            setSecondaryColor(Color.BLUE);
        } else if (secondary.cd() == 4) {
            secondary.setDmg(10).setKb(4);
            setSecondaryColor(Color.PURPLE);
        } else if (secondary.cd() == 5) {
            secondary.setDmg(15).setKb(5);
            setSecondaryColor(Color.RED);
            sound = "shoopdawhoop.chargedbeam.big";
            for(double i = 0; i < ((HitscanData) secondary.abilityData()).range(); i += 0.2) {
                Location current = targetLocation.clone().add(direction.clone().multiply(i));
                if(WallDetection.rayCast(current, ((HitscanData) secondary.abilityData()).radius())) {
                    break;
                }
                Particles.directionalRing(current, 0.5, Particle.DUST, new Particle.DustOptions(Color.WHITE, 1.0f));
            }
        }
        secondary.setCd(0);
        updateSecondaryDisplay();
        hitscan(player,AbilityType.SECONDARY,(HitscanData) secondary.abilityData());
        Sounds.playSoundToWorld(player, sound, 2.0f, 1.0f);
    }


    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
        Sounds.playSoundToPlayer(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        onRangedHit();
        switch (abilityType) {
            case SECONDARY -> {
                Interactions.handleInteraction(player.getLocation().getDirection(), secondary.dmg(), secondary.kb(), player, target);
                onRangedHit();
            }
            case ULTIMATE -> {
                if(ultimateHits.contains(target)) return;
                ultimateHits.add(target);
                Interactions.handleInteraction(player.getLocation().getDirection(), ultimate.dmg(), ultimate.kb(), player, target);
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> ultimateHits.remove(target), 8L);
            }
        }
    }


    @Override
    public void useUltimateAbility() {
        if(!ultimate.ready() || ultimate.inUse()) {
            player.sendMessage(NamedTextColor.RED + "Ultimate ability is on cooldown! " + (int) ultimate.timeUntilUse() + " seconds remaining.");
            return;
        }
        ultTimer();
        ultimate.setInUse(true);
        player.playSound(player.getLocation(), "shoopdawhoop.crystal", SoundCategory.MASTER, 5.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.0f), (20L));
        new BukkitRunnable(){
            private int timer = 0;

            @Override
            public void run(){
                timer++;
                if(timer >= ultimate.duration() * 20 / 3){
                    this.cancel();
                    return;
                }
                hitscan(player,AbilityType.ULTIMATE, (HitscanData) ultimate.abilityData());
            }
        }.runTaskTimer(Heroes.getInstance(), 20L, 3L);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> cooldown(ultimate), (long) (ultimate.duration() * 20));
    }



    @Override
    public void usePassiveSneak() {
        //can't use twice because u can't have >55 energy if ur on it already
        if(energy() < 55 || passive.inUse()) return;
        subtractEnergy(55);
        setCanIncreaseEnergy(false);

        ArmorStand armorStand = projectile(player,AbilityType.PASSIVE, (ProjectileData) passive.abilityData());
        armorStand.addPassenger(player);

        player.playSound(player.getLocation(), "shoopdawhoop.active", SoundCategory.MASTER, 3.0f, 1.0f);
        cooldown(passive);

        new BukkitRunnable(){
            Location lastLocation = armorStand.getLocation().clone();

            @Override
            public void run() {
                if(armorStand.isDead() || !armorStand.isValid() || energy() <= 0 || !player.isInsideVehicle()) {
                    player.leaveVehicle();
                    setCanIncreaseEnergy(true);
                    this.cancel();
                    return;
                }
                Location currentLocation = armorStand.getLocation();
                double distance = lastLocation.distance(currentLocation);
                subtractEnergy(distance);
                lastLocation = currentLocation.clone();
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 2L);
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() == player) {
            //prevent player from accidentally dismounting when activating
            if (passive.inUse() && energy() > 10) {
                event.setCancelled(true);
            }
            player.teleport(player.getLocation().subtract(event.getDismounted().getLocation().getDirection()));
        }
    }



    @Override
    protected void stats() {
        setHeroType(HeroType.RANGED);
        setWeight(3);

        primary = new Ability(AbilityType.PRIMARY, 1.5, 1, 0.3, new ProjectileData().setRadius(1.5).setRange(100).setCustomModelData(14003));
        secondary = new Ability(AbilityType.SECONDARY, 7, 1, 0, new HitscanData().setParticle(Particle.DUST).setDustOptions(Color.YELLOW, 1.0f));
        ultimate = new Ability(AbilityType.ULTIMATE, 6, 3,100, 4, new HitscanData().setCustomModelData(14004));
        ultimateHits = new HashSet<>();
        passive = new Ability(AbilityType.PASSIVE, 5, 4, 0, 0.5, new ProjectileData(1.5,1.5,100, 14003));

        setEnergyStats(100,100,0.3,true);
        initializeEnergyUpdates();
    }
}