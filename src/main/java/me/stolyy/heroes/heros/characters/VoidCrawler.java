package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.*;
import me.stolyy.heroes.heros.*;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.abilities.data.DashData;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.heros.abilities.interfaces.Dash;
import me.stolyy.heroes.utility.effects.Sounds;
import me.stolyy.heroes.utility.physics.Hitbox;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Set;


public class VoidCrawler extends HeroEnergy implements Dash {
    private double secondaryRange;

    public VoidCrawler(Player player) {
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
        Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg(), primary.kb(), player, target);
        Sounds.playSoundToPlayer(player, "melee.sword.meleehit", 1.0f, 1.0f);
    }

    @Override
    public void useSecondaryAbility() {
        if(!secondary.ready()) return;
        cooldown(secondary);



        Location startLocation = player.getLocation();
        Location eyeLocation = player.getEyeLocation();
        Vector direction = player.getEyeLocation().getDirection();
        Location teleportSpot = player.getEyeLocation();

        for (double i = 0; i < secondaryRange; i += 0.5) {
            Location currentLocation = eyeLocation.clone().add(direction.clone().multiply(i));
            teleportSpot = currentLocation;

            if (currentLocation.getBlock().getType().isSolid() || currentLocation.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                player.teleport(currentLocation.subtract(direction.clone().multiply(0.5)));
                i = secondaryRange +1;
            }

            //hit logic
            Set<Player> nearbyPlayers = Hitbox.sphere(currentLocation, 2);
            nearbyPlayers.remove(player);
            for (Player nearbyPlayer : nearbyPlayers) {
                    Interactions.handleInteraction(startLocation, secondary.dmg(), secondary.kb(), player, nearbyPlayer);
                    i = secondaryRange + 1;
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }

        player.teleport(teleportSpot);
        Sounds.playSoundToWorld(player, Sound.ENTITY_BLAZE_SHOOT, 3, 0f);
    }


    @Override
    public void useUltimateAbility() {
        if(ultimate.inUse() || !ultimate.ready()){
            player.sendMessage(NamedTextColor.RED + "Ultimate ability is on cooldown! " + (int) ultimate.timeUntilUse() + " seconds remaining.");
            return;
        }

        Sounds.playSoundToWorld(player, "duskcrawler.crystal", 5.0f, 1.0f);
        ultTimer();
        setJabCooldown(300);
        setJabReach(6.5);
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.18);
        //perma 100 void energy
        setEnergy(100);
        setCanIncreaseEnergy(false);

        //darkness
        Set<Player> targets = Hitbox.sphere(player.getLocation(), 75);
        for (Player target : targets) {
            if (Interactions.canInteract(player, target)) {
                PotionEffect darknessEffect = new PotionEffect(PotionEffectType.DARKNESS, 20 * (int) ultimate.duration(), 0);
                target.addPotionEffect(darknessEffect);
            }
        }

        primary.setCd(1.5);
        secondary.setCd(3.5);

        //end logic
        new BukkitRunnable() {
            @Override
            public void run() {
                setJabCooldown(500);
                setJabReach(5);
                primary.setCd(2);
                secondary.setCd(5);
                player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.14);
                setCanIncreaseEnergy(true);
                cooldown(ultimate);
            }
        }.runTaskLater(Heroes.getInstance(), (int) ultimate.duration() * 20L);
    }

    @Override
    public void onPunch(){
        super.onPunch();
        addEnergy(20);
    }

    public void updateAttackDamage() {
        Hero h = this;
        new BukkitRunnable() {
            @Override
            public void run() {
                if(HeroManager.getHero(player) != h){
                    this.cancel();
                    return;
                }

                setJabDamage(5 + energy() / 50);
                primary.setDmg(6 + energy() / 50);
                secondary.setDmg(7 + energy() / 50);
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    @Override
    protected void stats(){
        setWeight(2.0);
        setHeroType(HeroType.MELEE);

        primary = new Ability(AbilityType.PRIMARY, 6, 2, 2, new DashData());
        secondary = new Ability(AbilityType.SECONDARY, 7, 2.5, 5);
        secondaryRange = 15;
        ultimate = new Ability(AbilityType.ULTIMATE, 0, 0, 90, 10);

        setEnergyStats(0,100,-0.5,true);
        initializeEnergyUpdates();
    }
}
