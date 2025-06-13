package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.abilities.data.HitscanData;
import me.stolyy.heroes.heros.abilities.data.ProjectileData;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.heros.HeroType;
import me.stolyy.heroes.heros.abilities.interfaces.Hitscan;
import me.stolyy.heroes.heros.abilities.interfaces.PassiveDrop;
import me.stolyy.heroes.heros.abilities.interfaces.Projectile;
import me.stolyy.heroes.heros.abilities.interfaces.Reload;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.effects.Particles;
import me.stolyy.heroes.utility.effects.Sounds;
import me.stolyy.heroes.utility.physics.Hitbox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Skullfire extends HeroEnergy implements Hitscan, Projectile, Reload, PassiveDrop {
    private boolean isReloading;
    private int consecutiveHits;
    private int ammo;
    private Map<Player, Integer> chainCount;
    private int jumpCap;

    public Skullfire(Player player) {
        super(player);
    }

    @Override
    public void usePrimaryAbility() {
        if(primary.inUse() || isReloading || ammo <= 0){
            return;
        }

        primary.setInUse(true);
        int shotsPerClick = player.isSneaking() ? 3 : 1;

        new BukkitRunnable(){
            private int shots = 0;

            @Override
            public void run(){
                if(shots >= shotsPerClick || ammo == 0){
                    Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> primary.setInUse(false), 6L);
                    this.cancel();
                    return;
                }
                Sounds.playSoundToWorld(player, "skullfire.magnumshot", 1.0f, 1.0f);

                if(ultimate.inUse()) hitscan(player, AbilityType.ULTIMATE, (HitscanData) ultimate.abilityData());
                else hitscan(player, AbilityType.PRIMARY, (HitscanData) primary.abilityData());

                ammo--;
                shots++;
                updateAmmoDisplay();

                if(ammo == 0) reload();
            }
        }.runTaskTimer(Heroes.getInstance(), 0, 3L);
    }

    @Override
    public void reload() {
        if (isReloading) return;
        isReloading = true;
        Sounds.playSoundToWorld(player, "melee.reload", 2.0f, 1.0f);
        player.sendMessage(NamedTextColor.RED + "Reloading...");
        ItemStack gunItem = player.getInventory().getItem(0);
        cooldown(primary);

        if (gunItem != null && gunItem.getType() == Material.CARROT_ON_A_STICK) {
            new BukkitRunnable() {
                final int reloadTicks = (int) primary.cd() * 20;
                int currentTick = 0;

                @Override
                public void run() {
                    if (currentTick >= reloadTicks) {
                        ammo = 7;
                        isReloading = false;
                        updateAmmoDisplay();
                        updateItemDurability(0, (short) 0, 8002);
                        player.sendMessage(NamedTextColor.GREEN + "Reload complete!");
                        this.cancel();
                    } else {
                        updateItemDurability(0, (short) (25 - (25 * currentTick / reloadTicks)), 8002);
                        currentTick++;
                    }
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
        } else {
            // If the gun item is not found, just wait and complete the reload
            new BukkitRunnable() {
                @Override
                public void run() {
                    ammo = 7;
                    isReloading = false;
                    updateAmmoDisplay();
                    player.sendMessage(NamedTextColor.GREEN + "Reload complete!");
                }
            }.runTaskLater(Heroes.getInstance(), (long) (20*primary.cd()));
        }
    }

    @Override
    public void updateAmmoDisplay() {
        ItemStack primaryItem = player.getInventory().getItem(0);
        if (primaryItem != null) {
            ItemMeta meta = primaryItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(NamedTextColor.GOLD + "Skullfire Gun (" + ammo + "/7)"));
                primaryItem.setItemMeta(meta);
            }
            primaryItem.setAmount(Math.max(1, ammo)); // Ensure at least 1 is displayed
        }
    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
        if (!player.isOnGround())
            bullseye();

        consecutiveHits++;
        if (consecutiveHits >= 3) {
            secondary.setTimeUntilUse(0); // Reset grenade cooldown
            consecutiveHits = 0;
        }

        switch (abilityType) {
            case PRIMARY -> {
                Sounds.playSoundToPlayer(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg(), primary.kb(), player, target);
                onRangedHit();
            }
            case ULTIMATE -> {
                Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg() + ultimate.dmg(), primary.kb() + ultimate.kb(), player, target);
                Sounds.playSoundToWorld(target, Sound.BLOCK_CHAIN_HIT, 1.0f, 1.0f);
                chainCount.put(target, chainCount.getOrDefault(target, 0) + 1);
                chainUpdate(target);
            }
        }
    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {
        consecutiveHits = 0;
    }

    @Override
    public void useSecondaryAbility() {
        if(!secondary.ready()) return;

        player.playSound(player.getLocation(), "skullfire.grenadethrow", SoundCategory.MASTER, 2.0f, 1.0f);
        projectile(player, AbilityType.SECONDARY, (ProjectileData) secondary.abilityData());
        cooldown(secondary);
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        grenadeContact(location.add(0,1,0));
    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {
        grenadeContact(location.add(0,1,0));
    }

    public void grenadeContact(Location location) {
        Particles.summonParticle(location, Particle.EXPLOSION_EMITTER, null);
        Sounds.playSoundToWorld(location, "skullfire.explodegrenade", 3.0f, 1.0f);
        Set<Player> hitPlayers = Hitbox.sphere(location, 5);
        hitPlayers.remove(player);
        for (Player hitPlayer : hitPlayers) {
            Sounds.playSoundToPlayer(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            Interactions.handleInteraction(secondary.dmg(), secondary.kb(), player, hitPlayer);
        }
    }



    @Override
    public void useUltimateAbility() {
        if(!ultimate.ready() || ultimate.inUse()) {
            player.sendMessage(NamedTextColor.RED + "Ultimate ability is on cooldown! " + (int) ultimate.timeUntilUse() + " seconds remaining.");
            return;
        }

        ultimate.setInUse(true);
        ultTimer();
        ammo = 9;
        updateAmmoDisplay();
        player.playSound(player.getLocation(), "skullfire.crystal", SoundCategory.MASTER, 5.0f, 1.0f);

        new BukkitRunnable(){
            int timer = 0;
            @Override
            public void run() {
                if(ammo <= 0 || timer >= ultimate.duration() * 20L){
                    chainCount.clear();
                    cooldown(ultimate);
                }
                timer++;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    public void chainUpdate(Player target) {
        if(chainCount.getOrDefault(target, 0) == 1) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 1));
            Particles.ring(target.getLocation().add(0,1,0), 0.75, Particle.FLAME);
        } else if(chainCount.getOrDefault(target, 0) == 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 2));
            Particles.ring(target.getLocation().add(0,1,0), 0.75, Particle.FLAME);
            Particles.ring(target.getLocation().add(0,1.3,0), 0.75, Particle.FLAME);
        }
        if(chainCount.getOrDefault(target, 0) == 3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 16, 3));

            //stun for .25s
            new BukkitRunnable(){
                int ticksRun = 0;
                @Override
                public void run(){
                    if (ticksRun >= 8) {
                        this.cancel();
                        return;
                    }
                    target.setVelocity(new Vector(0,0,0));
                    ticksRun++;
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 1L);

            Particles.ring(target.getLocation().add(0,1,0), 0.75, Particle.FLAME);
            Particles.ring(target.getLocation().add(0,1.3,0), 0.75, Particle.FLAME);
            Particles.ring(target.getLocation().add(0,0.7,0), 0.75, Particle.FLAME);
            chainCount.put(target,0);
        }
    }

    @Override
    public void usePassiveDrop() {
        reload();
    }

    private void bullseye() {
        if(maxDoubleJumps() < jumpCap) {
            setMaxDoubleJumps(maxDoubleJumps() + 1);
            setCanDoubleJump(true);
            player.setAllowFlight(true);
        }
        addEnergy(1);
    }

    @Override
    public void doubleJump(){
        if(doubleJumpCount() < 2) super.doubleJump();
        else {
            setDoubleJumpCount(doubleJumpCount() + 1);
            if(doubleJumpCount() >= maxDoubleJumps()) setCanDoubleJump(false);
            Particles.ring(player.getLocation(), .75, Particle.SMOKE);
            Sounds.playSoundToPlayer(player, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
            player.setVelocity(player.getLocation().getDirection().multiply(0.90).setY(0.85));
        }
    }

    @Override
    public void resetDoubleJumps(){
        super.resetDoubleJumps();
        setEnergy(2);
    }

    @Override
    protected void stats(){
        setWeight(2);
        setHeroType(HeroType.RANGED);

        primary = new Ability(AbilityType.PRIMARY, 3, 0.9,2, new HitscanData().setRange(100).setParticle(Particle.ASH));
        consecutiveHits = 0;
        ammo = 7;
        isReloading = false;
        secondary = new Ability(AbilityType.SECONDARY, 7, 3, 8, new ProjectileData(2, 0.5, 70, true, 8003));
        //dmg/kb is added on top of primary stats
        ultimate = new Ability(AbilityType.ULTIMATE, 1, .5, 100, 10, new HitscanData().setRange(120).setParticle(Particle.FLAME));
        chainCount = new HashMap<>();
        jumpCap = 5;

        setEnergyStats(2,jumpCap,0,false);
        initializeEnergyUpdates();
    }
}
