package me.stolyy.heroes.utility.effects;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public class Sounds {
    public static void playSoundToPlayer(Player player, String sound, float volume, float pitch) {
        if (player != null && sound != null) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public static void playSoundToPlayer(Player player, Sound sound, float volume, float pitch) {
        if (player != null && sound != null) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public static void playSoundToWorld(Player player, String sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }

        for(Player p : player.getWorld().getPlayers()) {
            p.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public static void playSoundToWorld(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }

        for(Player p : player.getWorld().getPlayers()) {
            p.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public static void playSoundToWorld(Location location, Sound sound, float volume, float pitch) {
        if (location == null || sound == null) {
            return;
        }

        for(Player p : location.getWorld().getPlayers()) {
            p.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public static void playSoundToWorld(Location location, String sound, float volume, float pitch) {
        if (location == null || sound == null) {
            return;
        }

        for(Player p : location.getWorld().getPlayers()) {
            p.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
        }
    }
}
