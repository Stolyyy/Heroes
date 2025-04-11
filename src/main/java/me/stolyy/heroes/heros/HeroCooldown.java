package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.abilities.AbilityType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class HeroCooldown extends Hero {
    private static final Plugin PLUGIN = Heroes.getInstance();
    private static final int UPDATES_PER_SECOND = 10;

    public HeroCooldown(Player player){
        super(player);
    }

    public void onPunch() {
        int reduce = 0;
        switch (heroType) {
            case MELEE -> reduce = 3;
            case HYBRID -> reduce = 1;
        }
        ultimate.timeUntilUse -= reduce;
    }

    public void onRangedHit() {
        int reduce = 0;
        switch (heroType) {
            case RANGED -> reduce = 2;
            case HYBRID -> reduce = 1;
        }
        ultimate.timeUntilUse -= reduce;
    }

    public void resetUltTimer(){
        cooldown(ultimate);
    }

    protected void cooldown(Ability ability){
        AbilityType abilityType = ability.abilityType;
        ability.ready = false;
        ability.timeUntilUse = ability.cd;
        switch (abilityType){
            case PRIMARY -> {
                runCooldown(ability, null);
            }
            case SECONDARY -> {
                ItemStack cooldownItem = player.getInventory().getItem(1);
                if (cooldownItem != null && cooldownItem.getType() == Material.CARROT_ON_A_STICK) {
                    runCooldown(ability, cooldownItem);
                }
            } case ULTIMATE -> {
                ability.inUse = false;
                ItemStack ultimateItem = player.getInventory().getItem(2);
                if (ultimateItem != null && ultimateItem.getType() == Material.CARROT_ON_A_STICK) {
                    runCooldown(ability, ultimateItem);
                }
            }
        }
    }

    protected void ultTimer(){
        new BukkitRunnable() {
            int elapsedTime = 0;
            @Override
            public void run() {
                String title;
                if (elapsedTime < 0.4 * ultimate.duration) {
                    title = "" + ChatColor.DARK_GREEN;
                } else if (elapsedTime < 0.7 * ultimate.duration) {
                    title = "" + ChatColor.YELLOW;
                } else {
                    title = "" + ChatColor.RED;
                }
                player.sendTitle(title  + (int) (ultimate.duration - elapsedTime), "", 5, 25, 10);
                elapsedTime++;

                if (elapsedTime == ultimate.duration) {
                    this.cancel();
                }
            }
        }.runTaskTimer(PLUGIN, 0L, 20L);

    }



    private void runCooldown(Ability ability, ItemStack cooldownItem){
        AbilityType abilityType = ability.abilityType;

        new BukkitRunnable() {
            @Override
            public void run() {
                double percentage = (ability.timeUntilUse / ability.cd);
                //End Logic
                if (ability.timeUntilUse <= 0) {
                    ability.ready = true;
                    switch (abilityType){
                        case PRIMARY -> player.sendActionBar(ChatColor.GREEN + "Primary Ability Ready!");
                        case SECONDARY -> updateItemDurability(cooldownItem, 1, (short) 0, 5);
                        case ULTIMATE -> {
                            player.sendMessage(ChatColor.GREEN + "Ultimate Ability Ready!");
                            updateItemDurability(cooldownItem, 2, (short) 0, 7);
                        }
                    }
                    this.cancel();
                    return;
                }
                //Run every update
                switch (abilityType){
                    case PRIMARY -> player.sendActionBar(ChatColor.GREEN + "Primary Cooldown: " + createProgressBar(percentage));
                    case SECONDARY -> updateItemDurability(cooldownItem, 1, percentage, 4);
                    case ULTIMATE -> updateItemDurability(cooldownItem, 2, percentage, 6);
                }
                ability.timeUntilUse = Math.max(0, ability.timeUntilUse - (1.0 / UPDATES_PER_SECOND));
            }

        }.runTaskTimer(PLUGIN, 0L, 20L / UPDATES_PER_SECOND);
    }

    private void updateItemDurability(ItemStack item, int slot, double percentRemaining, int customModelData) {
        percentRemaining = Math.min(1, percentRemaining);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable dMeta) {
            dMeta.setCustomModelData(customModelData);
            short maxDurability = item.getType().getMaxDurability();
            short damage = (short) (maxDurability * (1 - percentRemaining));
            dMeta.setDamage(damage);
            item.setItemMeta(dMeta);
            player.getInventory().setItem(slot, item);
        }
    }

    private String createProgressBar(double percentage) {
        percentage = Math.min(1, percentage);
        int totalBars = 10;
        int filledBars = (int) (percentage / 10.0);
        return ChatColor.GREEN +
                "|".repeat(Math.max(0, filledBars)) +
                ChatColor.RED +
                "|".repeat(Math.max(0, totalBars - filledBars));
    }
}
