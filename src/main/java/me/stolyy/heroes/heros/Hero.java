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

import java.util.HashMap;
import java.util.Map;

public abstract class Hero {
    protected final Player player;
    public HeroType heroType;
    public double weight;

    protected Ability primary;
    protected Ability secondary;
    protected Ability ultimate;
    protected boolean inUltimate = false;
    protected double ultDuration;

    public Hero(Player player){
        this.player = player;
        stats();
    }

    public abstract void usePrimaryAbility();
    public abstract void useSecondaryAbility();
    public abstract void useUltimateAbility();
    public abstract void passiveAbility1();
    public abstract void passiveAbility2();

    protected abstract void stats();
}