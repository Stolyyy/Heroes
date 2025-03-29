package me.stolyy.heroes;

import me.stolyy.heroes.heros.HeroType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Cooldowns {
    private JavaPlugin plugin = Heroes.getInstance();;
    private Player player;
    private boolean primaryReady;
    private boolean secondaryReady;
    private int maxUltimateCooldown;
    private int ultimateCooldown;
    private HeroType heroType;

    public Cooldowns(Player player, HeroType heroType, int ultimateCooldown) {
        this.player = player;
        this.heroType = heroType;
        this.primaryReady = true;
        this.secondaryReady = true;
        this.maxUltimateCooldown = 10 * ultimateCooldown;
        this.ultimateCooldown = 0;
    }

    public void usePrimaryAbility(double cooldownSeconds) {
        primaryReady = false;
        new BukkitRunnable() {
            double remainingCooldown = cooldownSeconds * 10; // Convert seconds to ticks

            @Override
            public void run() {
                if (remainingCooldown <= 0) {
                    primaryReady = true;
                    player.sendActionBar(ChatColor.GREEN + "Primary Ability Ready!");
                    this.cancel();
                } else {
                    int percentage = (int) (100 - (remainingCooldown / (cooldownSeconds * 10) * 100));
                    String bar = createProgressBar(percentage);
                    player.sendActionBar(ChatColor.GOLD + "Primary Cooldown: " + bar + " " + (remainingCooldown / 10));
                    remainingCooldown--;
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    //Visual display for primary's cooldown
    private String createProgressBar(int percentage) {
        int totalBars = 10;
        int filledBars = (int) (percentage / 10.0);
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        for (int i = 0; i < filledBars; i++) {
            bar.append("|");
        }
        bar.append(ChatColor.RED);
        for (int i = filledBars; i < totalBars; i++) {
            bar.append("|");
        }
        return bar.toString();
    }

    public void useSecondaryAbility(double cooldownSeconds) {
        secondaryReady = false;
        ItemStack cooldownItem = player.getInventory().getItem(1);
        if (cooldownItem != null && cooldownItem.getType() == Material.CARROT_ON_A_STICK) {
            new BukkitRunnable() {
                double remainingCooldown = cooldownSeconds * 10; // Convert seconds to ticks

                @Override
                public void run() {
                    if (remainingCooldown <= 0) {
                        secondaryReady = true;
                        updateItemDurability(cooldownItem, (short) 0, 5);
                        this.cancel();
                    } else {
                        short durability = (short) (25 - (25 * remainingCooldown / (cooldownSeconds * 10)));
                        updateItemDurability(cooldownItem, durability, 4);
                        remainingCooldown--;
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    //Visual display for ultimate/secondary cooldown
    private void updateItemDurability(ItemStack item, short durability, int customModelData) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta instanceof Damageable) {
            meta.setCustomModelData(customModelData);
            ((Damageable) meta).setDamage(25 - durability);  // Invert the durability
            item.setItemMeta(meta);
        }
    }

    public void useUltimateAbility() {
        ultimateCooldown = maxUltimateCooldown;
        ItemStack ultimateItem = player.getInventory().getItem(2);
        if (ultimateItem != null && ultimateItem.getType() == Material.CARROT_ON_A_STICK) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (ultimateCooldown <= 0) {
                        player.sendMessage(ChatColor.GREEN + "Ultimate Ability Ready!");
                        updateItemDurability(ultimateItem, (short) 0, 7);
                        this.cancel();
                    } else {
                        short durability = (short) (25 - (25 * ultimateCooldown / maxUltimateCooldown));
                        updateItemDurability(ultimateItem, durability, 6);
                        ultimateCooldown--;
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    public void reduceUltimateCooldown(int seconds) {
        int ticks = seconds * 10; // Convert seconds to ticks
        ultimateCooldown = Math.max(0, ultimateCooldown - ticks);
        ItemStack ultimateItem = player.getInventory().getItem(2);
        if (ultimateItem != null && ultimateItem.getType() == Material.CARROT_ON_A_STICK) {
            short durability = (short) (25 - (25 * ultimateCooldown / maxUltimateCooldown));
            updateItemDurability(ultimateItem, durability, 6);
        }
    }

    public void onPunch() {
        switch (heroType) {
            case MELEE:
                reduceUltimateCooldown(3);
                break;
            case HYBRID:
                reduceUltimateCooldown(1);
                break;
        }
    }

    public void onRangedHit() {
        switch (heroType) {
            case RANGED:
                reduceUltimateCooldown(2);
                break;
            case HYBRID:
                reduceUltimateCooldown(1);
                break;
        }
    }

    // Getters
    public boolean isPrimaryReady() {
        return primaryReady;
    }

    public boolean isSecondaryReady() {
        return secondaryReady;
    }

    public int getUltimateCooldown() {
        return ultimateCooldown / 10; // Convert ticks back to seconds for external use
    }
}