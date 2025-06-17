package me.stolyy.heroes.game.minigame;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class GameEffects {
    private static final Set<UUID> restricted = new HashSet<>();

    public static void applyEffects(Player player, TeamSettings teamSettings){
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(teamSettings.maxHealth());
        player.setHealth(teamSettings.maxHealth());
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0));
    }

    public static void removeEffects(Player player){
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setHealthScaled(false);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.SATURATION);
    }

    public static void restrictPlayer(Player player){
        restricted.add(player.getUniqueId());
    }
    public static void unRestrictPlayer(Player player){
        restricted.remove(player.getUniqueId());
    }

    public static boolean isRestricted(Player player){
        return restricted.contains(player.getUniqueId());
    }
}
