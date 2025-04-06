package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.heros.abilities.*;
import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.heros.HeroType;
import me.stolyy.heroes.utility.Interactions;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Skullfire extends HeroEnergy implements Hitscan, Projectile, Reload {
    private boolean isReloading;
    private int consecutiveHits;
    private int ammo;
    private int maxDoubleJumps;
    private Map<Player, Integer> chainCount;

    public Skullfire(Player player) {
        super(player);
    }

    @Override
    public void usePrimaryAbility() {
        if(primary.inUse || isReloading || ammo <= 0){
            return;
        }

        primary.inUse = true;
        int shotsPerClick = player.isSneaking() ? 3 : 1;

        new BukkitRunnable(){
            private int shots = 0;

            @Override
            public void run(){
                if(shots >= shotsPerClick || ammo == 0){
                    Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> primary.inUse = false, 8L);
                    this.cancel();
                    return;
                }
                player.playSound(player.getLocation(), "skullfire.magnumshot", SoundCategory.MASTER, 1.0f, 1.0f);

                if(ultimate.inUse)
                    Hitscan.hitscan(player, AbilityType.ULTIMATE, 100, 1, Particle.SOUL_FIRE_FLAME, Color.WHITE);
                else Hitscan.hitscan(player, AbilityType.PRIMARY, 100, 1, Particle.ASH, Color.WHITE);

                ammo--;
                shots++;
                updateAmmoDisplay();

                if(ammo == 0) reload();
            }
        }.runTaskTimer(Heroes.getInstance(), 0, 3L);
    }

    private void reload() {
        if (isReloading) return;
        isReloading = true;
        player.playSound(player.getLocation(), "melee.reload", SoundCategory.MASTER, 1.0f, 1.0f);
        player.sendMessage(ChatColor.RED + "Reloading...");
        ItemStack gunItem = player.getInventory().getItem(0);
        cooldown(primary);

        if (gunItem != null && gunItem.getType() == Material.CARROT_ON_A_STICK) {
            new BukkitRunnable() {
                int reloadTicks = (int) primary.cd * 20;
                int currentTick = 0;

                @Override
                public void run() {
                    if (currentTick >= reloadTicks) {
                        ammo = 7;
                        isReloading = false;
                        updateAmmoDisplay();
                        updateItemDurability(gunItem, (short) 0);
                        player.sendMessage(ChatColor.GREEN + "Reload complete!");
                        this.cancel();
                    } else {
                        short durability = (short) (25 - (25 * currentTick / reloadTicks));
                        updateItemDurability(gunItem, durability);
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
                    player.sendMessage(ChatColor.GREEN + "Reload complete!");
                }
            }.runTaskLater(Heroes.getInstance(), (long) (20*primary.cd));
        }
    }

    private void updateItemDurability(ItemStack item, short durability) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable) {
            meta.setCustomModelData(8002);
            ((Damageable) meta).setDamage(25 - durability);  // Invert the durability
            item.setItemMeta(meta);
        }
    }

    private void updateAmmoDisplay() {
        ItemStack primaryItem = player.getInventory().getItem(0);
        if (primaryItem != null) {
            ItemMeta meta = primaryItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Skullfire Gun (" + ammo + "/7)");
                primaryItem.setItemMeta(meta);
            }
            primaryItem.setAmount(Math.max(1, ammo)); // Ensure at least 1 is displayed
        }
    }

    @Override
    public void onHitscanHit(Player target, Location location, AbilityType abilityType) {
        if (!player.isOnGround()) {
            increaseMaxDoubleJumps();
        }

        consecutiveHits++;
        if (consecutiveHits >= 3) {
            secondary.timeUntilUse = 0; // Reset grenade cooldown
            consecutiveHits = 0;
        }

        switch (abilityType) {
            case PRIMARY:
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg, primary.kb, player, target);
                break;
            case ULTIMATE:
                Interactions.handleInteraction(player.getEyeLocation().getDirection(), primary.dmg + ultimate.dmg, primary.kb + ultimate.kb, player, target);
                player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_HIT, 1.0f, 1.0f);
                chainCount.put(target, chainCount.getOrDefault(target, 0) + 1);
                chainUpdate(target);
                break;
        }
    }

    private void increaseMaxDoubleJumps() {
        int currentMax = AbilityListener.getMaxDoubleJumps(player);
        if (currentMax < maxDoubleJumps) {
            AbilityListener.setMaxDoubleJumps(player,
                    AbilityListener.getMaxDoubleJumps(player) + 1);
        }
        energy = Math.min(currentMax + 1, maxDoubleJumps);
    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {
        consecutiveHits = 0;
    }



    @Override
    public void useSecondaryAbility() {
        if(!secondary.ready) return;

        player.playSound(player.getLocation(), "skullfire.grenadethrow", SoundCategory.MASTER, 2.0f, 1.0f);
        Projectile.projectile(player, AbilityType.SECONDARY, 2, 0.5, true, 8003);
        cooldown(secondary);
    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {
        grenadeContact(location);
    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {
        grenadeContact(location);
    }

    public void grenadeContact(Location location) {
        location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 1);
        player.playSound(location, "skullfire.explodegrenade", SoundCategory.MASTER, 3.0f, 1.0f);
        List<Player> nearbyPlayers = (List<Player>) player.getWorld().getNearbyPlayers(location, 4, 4, 4);
        for (Player nearbyPlayer : nearbyPlayers) {
            if (nearbyPlayer != player) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                Interactions.handleInteraction(secondary.dmg, secondary.kb, player, nearbyPlayer);
            }
        }
    }



    @Override
    public void useUltimateAbility() {
        if(!ultimate.ready || ultimate.inUse) {
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + ultimate.timeUntilUse + " seconds remaining.");
            return;
        }

        ultimate.inUse = true;
        ultTimer();
        ammo = 9;
        updateAmmoDisplay();
        player.playSound(player.getLocation(), "skullfire.crystal", SoundCategory.MASTER, 5.0f, 1.0f);

        new BukkitRunnable(){
            int timer = 0;
            @Override
            public void run() {
                if(ammo <= 0 || timer >= ultimate.duration * 20L){
                    cooldown(ultimate);
                }
                timer++;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    public void chainUpdate(Player target) {
        if(chainCount.getOrDefault(target, 0) == 1) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 1));
            createParticleRing(target,1, 40);
        } else if(chainCount.getOrDefault(target, 0) == 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 2));
            createParticleRing(target,1, 40);
            createParticleRing(target,0.6, 40);
        }
        if(chainCount.getOrDefault(target, 0) == 3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 3));

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

            createParticleRing(target,1, 40);
            createParticleRing(target,0.6, 40);
            createParticleRing(target,1.5, 20);
            chainCount.put(target,0);
        }
    }

    public void createParticleRing(Player player, double heightOffset, double duration) {
        final double radius = 1.0; // Radius of the ring
        final int particlesPerRing = 16; // Number of particles in each ring

        new BukkitRunnable() {
            int ticksRun = 0;

            @Override
            public void run() {
                if (ticksRun >= duration) {
                    this.cancel();
                    return;
                }

                Location playerLocation = player.getLocation().add(0, heightOffset, 0); // Add height offset here
                Vector upVector = new Vector(0, 1, 0);

                for (int i = 0; i < particlesPerRing; i++) {
                    double angle = 2 * Math.PI * i / particlesPerRing;
                    Vector offset = new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    Vector rotated = rotateAroundAxis(offset, upVector, playerLocation.getYaw());

                    Location particleLocation = playerLocation.clone().add(rotated);
                    player.getWorld().spawnParticle(Particle.FLAME, particleLocation, 1, 0, 0, 0, 0);
                }

                ticksRun++;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    private Vector rotateAroundAxis(Vector vector, Vector axis, double angle) {
        angle = Math.toRadians(angle);
        Vector parallel = axis.multiply(axis.dot(vector));
        Vector perpendicular = vector.subtract(parallel);
        Vector crossProduct = axis.crossProduct(perpendicular);
        return parallel.add(perpendicular.multiply(Math.cos(angle))).add(crossProduct.multiply(Math.sin(angle)));
    }

    @Override
    public void passiveAbility1() {
    }

    @Override
    public void passiveAbility2() {

    }

    @Override
    protected void stats(){
        weight = 2;
        heroType = HeroType.RANGED;

        primary = new Ability(AbilityType.PRIMARY, 3, 0.9,1.5);
        consecutiveHits = 0;
        ammo = 7;
        isReloading = false;
        secondary = new Ability(AbilityType.SECONDARY, 7, 3, 8);
        //dmg/kb is added on top of primary stats
        ultimate = new Ability(AbilityType.ULTIMATE, 1, .5, 100, 10);
        chainCount = new HashMap<>();
        maxDoubleJumps = 5;

        setEnergyStats(2,5,0,false);
        initializeEnergyUpdates();
    }
}
