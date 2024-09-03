package me.stolyy.heroes.heros;

import me.stolyy.heroes.AbilityListener;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Utility.Interactions;
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

public class Skullfire extends Hero implements Hitscan, Projectile{
    Player player;
    final double weight = 2;
    Cooldowns cooldowns;
    @Override
    public double getWeight() {
        return weight;
    }
    public HeroType getHeroType() {
        return HeroType.RANGED;
    }
    AbilityListener jumpCap;
    boolean inPrimary = false;
    boolean isReloading = false;
    int consecutiveHits = 0;
    double primaryDMG = 3;
    double primaryKB = 0.9;
    double primaryCD = 1.5;
    double secondaryDMG = 7;
    double secondaryKB = 3;
    double secondaryCD = 8;
    int ammo = 7;
    int ultTime = 10;
    boolean inUltimate = false;
    Map<Player, Integer> chainCount = new HashMap<>();

    public Skullfire(Player player) {
        this.player=player;
        this.jumpCap = new AbilityListener(Heroes.getInstance().getHeroManager(), Heroes.getInstance().getGameManager());
        this.cooldowns = new Cooldowns(player, HeroType.RANGED, 100);
    }

    @Override
    public void usePrimaryAbility(Player player) {
        if (inPrimary || isReloading) {
            return;
        }

        if (ammo > 0) {
            inPrimary = true;
            Hero h = this;
            int shotsPerClick = player.isSneaking() ? 3 : 1;
            new BukkitRunnable() {
                private int count = 0;
                @Override
                public void run() {
                    if (count >= shotsPerClick || ammo == 0) {
                        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inPrimary = false, 8L);
                        this.cancel();
                        return;
                    }
                    player.playSound(player.getLocation(), "skullfire.magnumshot", SoundCategory.MASTER, 1.0f, 1.0f);
                    if (inUltimate) {
                        Hitscan.hitscan(100, player.getEyeLocation(), player.getEyeLocation().getDirection(), Particle.SOUL_FIRE_FLAME, Color.WHITE, player, h, AbilityType.ULTIMATE);
                    } else {
                        Hitscan.hitscan(100, player.getEyeLocation(), player.getEyeLocation().getDirection(), Particle.ASH, Color.WHITE, player, h, AbilityType.PRIMARY);
                    }
                    ammo--;
                    count++;
                    updateAmmoDisplay();
                    if (ammo == 0) {
                        reload();
                    }
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 3L);


        }
    }

    private void reload() {
        if (isReloading) return;
        isReloading = true;
        player.playSound(player.getLocation(), "melee.reload", SoundCategory.MASTER, 1.0f, 1.0f);
        player.sendMessage(ChatColor.RED + "Reloading...");

        ItemStack gunItem = player.getInventory().getItem(0);
        if (gunItem != null && gunItem.getType() == Material.CARROT_ON_A_STICK) {
            new BukkitRunnable() {
                int reloadTicks = 30; // 1.5 seconds * 20 ticks
                int currentTick = 0;

                @Override
                public void run() {
                    if (currentTick >= reloadTicks) {
                        ammo = 7;
                        isReloading = false;
                        updateAmmoDisplay();
                        updateItemDurability(gunItem, (short) 0, 8002);
                        player.sendMessage(ChatColor.GREEN + "Reload complete!");
                        this.cancel();
                    } else {
                        short durability = (short) (25 - (25 * currentTick / reloadTicks));
                        updateItemDurability(gunItem, durability, 8002);
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
            }.runTaskLater(Heroes.getInstance(), 30L);
        }
    }

    private void updateItemDurability(ItemStack item, short durability, int customModelData) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta instanceof Damageable) {
            meta.setCustomModelData(customModelData);
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
            cooldowns.useSecondaryAbility(0); // Reset grenade cooldown
            consecutiveHits = 0;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        switch (abilityType) {
            case PRIMARY:
                Interactions.handleInteractions(player.getEyeLocation().getDirection(), primaryKB, primaryDMG, player, target);
                break;
            case ULTIMATE:
                Interactions.handleInteractions(player.getEyeLocation().getDirection(), primaryKB*1.2, primaryDMG+1, player, target);
                chainCount.put(target, chainCount.getOrDefault(target, 0)+1);
                chainUpdate(target);
                break;
        }
    }
    private void increaseMaxDoubleJumps() {
        int currentMax = jumpCap.getMaxDoubleJumps(player);
        if (currentMax < 7) {
            jumpCap.setMaxDoubleJumps(player, currentMax + 1);
            player.sendMessage(ChatColor.YELLOW + "Max double jumps increased to " + (currentMax + 1) + "!");
        }
    }

    @Override
    public void onHitscanHitWall(Location location, AbilityType abilityType) {
        consecutiveHits = 0;
    }

    @Override
    public void useSecondaryAbility(Player player) {
        if(cooldowns.isSecondaryReady()) {
            player.playSound(player.getLocation(), "skullfire.grenadethrow", SoundCategory.MASTER, 2.0f, 1.0f);
            Projectile.projectile(player,2,true, 8003, 0.5, this, AbilityType.SECONDARY);
            cooldowns.useSecondaryAbility(secondaryCD);
        }

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
                Interactions.handleInteractions(player.getLocation(), secondaryKB, secondaryDMG, player, nearbyPlayer);
                return;
            }
        }
    }

    @Override
    public void useUltimateAbility(Player player) {
        if (cooldowns.getUltimateCooldown() == 0) {
            inUltimate = true;
            cooldowns.useUltimateAbility();
            player.playSound(player.getLocation(), "skullfire.crystal", SoundCategory.MASTER, 5.0f, 1.0f);
            new UltTimer(player, ultTime).runTaskTimer(Heroes.getInstance(), 0L, 20L);
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> inUltimate = false, (ultTime * 20L));
        } else {
            player.sendMessage(ChatColor.RED + "Ultimate ability is on cooldown! " + cooldowns.getUltimateCooldown() + " seconds remaining.");
        }
    }

    public void chainUpdate(Player target) {
        double currentHealth = target.getHealth();
        if(chainCount.getOrDefault(target, 0) == 1) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 1));
            createParticleRing(target,1, 40);
        } else if(chainCount.getOrDefault(target, 0) == 2) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 2));
            createParticleRing(target,0.6, 40);
        }
        if(chainCount.getOrDefault(target, 0) == 3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 3));
            new BukkitRunnable(){
                int ticksRun = 0;
                @Override
                public void run(){
                    if (ticksRun >= 5) {
                        this.cancel();
                        return;
                    }
                    target.setVelocity(new Vector(0,0,0));
                    ticksRun++;
                }
            }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
            createParticleRing(target,1.5, 20);
            if (currentHealth > 1.0) {
                target.setHealth(Math.max(1.0, currentHealth - 5));
            } else if (currentHealth <= 1.0) {
                target.damage(target.getHealth(), player);
            }
            chainCount.put(target,0);
        }
    }

    public void createParticleRing(Player player, double heightOffset, double duration) {
        final double radius = 1.0; // Radius of the ring
        final int particlesPerRing = 20; // Number of particles in each ring

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
    public void passiveAbility1(Player player) {
    }

    @Override
    public void passiveAbility2(Player player) {

    }




}
