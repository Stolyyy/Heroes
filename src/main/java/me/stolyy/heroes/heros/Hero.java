package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public abstract class Hero {
    private static final Plugin PLUGIN = Heroes.getInstance();
    public final Player player;

    //constants
    public HeroType heroType;
    public double weight;

    protected Map<AbilityType, Boolean> abilityReady;
    protected Map<AbilityType, Double> abilityDMG;
    protected Map<AbilityType, Double> abilityKB;
    protected Map<AbilityType, Double> abilityCD;
    protected Map<AbilityType, Double> timeUntilUse;
    protected boolean inUltimate;
    protected double ultDuration;

    public Hero(Player player){
        this.player = player;
        abilityReady = new HashMap<>(5);
        abilityDMG = new HashMap<>(5);
        abilityKB = new HashMap<>(5);
        abilityCD = new HashMap<>(5);
        timeUntilUse = new HashMap<>(5);
        stats();
        timeUntilUse.put(AbilityType.PRIMARY,abilityCD.get(AbilityType.PRIMARY));
        timeUntilUse.put(AbilityType.SECONDARY,abilityCD.get(AbilityType.SECONDARY));
        timeUntilUse.put(AbilityType.ULTIMATE,abilityCD.get(AbilityType.ULTIMATE));
    }

    protected abstract void stats();

    public abstract void usePrimaryAbility();
    public abstract void useSecondaryAbility();
    public abstract void useUltimateAbility();
    public abstract void passiveAbility1();
    public abstract void passiveAbility2();

    //cooldowns

    public void onPunch() {
        int reduce = 0;
        switch (heroType) {
            case MELEE -> reduce = 3;
            case HYBRID -> reduce = 1;
        }
        timeUntilUse.put(AbilityType.ULTIMATE,timeUntilUse.get(AbilityType.ULTIMATE) - reduce);
    }

    public void onRangedHit() {
        int reduce = 0;
        switch (heroType) {
            case RANGED -> reduce = 2;
            case HYBRID -> reduce = 1;
        }
        timeUntilUse.put(AbilityType.ULTIMATE,timeUntilUse.get(AbilityType.ULTIMATE) - reduce);
    }

    public void resetUltTimer(){
        abilityReady.put(AbilityType.ULTIMATE, false);
        timeUntilUse.put(AbilityType.ULTIMATE,abilityCD.get(AbilityType.ULTIMATE));
    }


    private static final int UPDATES_PER_SECOND = 10;

    protected void cooldown(AbilityType abilityType){
        abilityReady.put(abilityType, false);
        timeUntilUse.put(abilityType, abilityCD.get(abilityType));
        switch (abilityType){
            case PRIMARY -> runCooldown(abilityType,null);
            case SECONDARY -> {
                ItemStack cooldownItem = player.getInventory().getItem(1);
                if (cooldownItem != null && cooldownItem.getType() == Material.CARROT_ON_A_STICK) {
                    runCooldown(abilityType,cooldownItem);
                }
            } case ULTIMATE -> {
                ItemStack ultimateItem = player.getInventory().getItem(2);
                if (ultimateItem != null && ultimateItem.getType() == Material.CARROT_ON_A_STICK) {
                    runCooldown(abilityType,ultimateItem);
                }
            }
        }
    }

    private void runCooldown(AbilityType abilityType, ItemStack cooldownItem){
        new BukkitRunnable() {
            @Override
            public void run() {
                //End Logic
                if (timeUntilUse.get(abilityType) <= 0) {
                    abilityReady.put(abilityType, true);
                    switch (abilityType){
                        case PRIMARY -> player.sendActionBar(ChatColor.GREEN + "Primary Ability Ready!");
                        case SECONDARY -> updateItemDurability(cooldownItem, (short) 0, 5);
                        case ULTIMATE -> {
                            player.sendMessage(ChatColor.GREEN + "Ultimate Ability Ready!");
                            updateItemDurability(cooldownItem, (short) 0, 7);
                        }
                    }
                    this.cancel();

                //Run every update
                } else {
                    switch (abilityType){
                        case PRIMARY -> player.sendActionBar(ChatColor.GREEN + "Primary Ability Ready!");
                        case SECONDARY -> updateItemDurability(cooldownItem, timeUntilUse.getOrDefault(abilityType,abilityCD.get(abilityType))
                                / abilityCD.get(AbilityType.SECONDARY) / UPDATES_PER_SECOND, 4);
                        case ULTIMATE -> updateItemDurability(cooldownItem, timeUntilUse.getOrDefault(abilityType,abilityCD.get(abilityType))
                                / abilityCD.get(AbilityType.ULTIMATE), 6);
                    }
                    timeUntilUse.put(abilityType, timeUntilUse.get(abilityType) - (1.0 / UPDATES_PER_SECOND));
                }
            }

        }.runTaskTimer(PLUGIN, 0L, 20L / UPDATES_PER_SECOND);
    }

    protected void ultTimer(){
        new BukkitRunnable() {
            int elapsedTime = 0;
            @Override
            public void run() {
                String title;
                if (elapsedTime < 0.4 * ultDuration) {
                    title = "" + ChatColor.DARK_GREEN;
                } else if (elapsedTime < 0.7 * ultDuration) {
                    title = "" + ChatColor.YELLOW;
                } else {
                    title = "" + ChatColor.RED;
                }
                player.sendTitle(title  + (ultDuration - elapsedTime), "", 5, 25, 10);
                elapsedTime++;

                if (elapsedTime == ultDuration) {
                    this.cancel();
                }
            }
        }.runTaskTimer(PLUGIN, 0L, 20L);

    }



    private void updateItemDurability(ItemStack item, double percentDone, int customModelData) {
        short durability = (short) (25 * percentDone);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable) {
            meta.setCustomModelData(customModelData);
            ((Damageable) meta).setDamage(25 - durability);
            item.setItemMeta(meta);
        }
    }

    private String createProgressBar(int percentage) {
        int totalBars = 10;
        int filledBars = (int) (percentage / 10.0);
        return ChatColor.GREEN +
                "|".repeat(Math.max(0, filledBars)) +
                ChatColor.RED +
                "|".repeat(Math.max(0, totalBars - filledBars));
    }


}