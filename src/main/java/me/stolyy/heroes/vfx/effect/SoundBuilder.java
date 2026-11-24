package me.stolyy.heroes.vfx.effect;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

public class SoundBuilder {
    private static final double VANILLA_AUDIBLE_RANGE_PER_VOL = 16.0;
    private static final double DISTANT_PROXY_OFFSET_BLOCKS = 8.0;

    private final String soundKey;
    private final Sound bukkitSound;
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private float pitchVariation = 0.0f;

    private SoundBuilder(String soundKey) {
        this.soundKey = soundKey.toLowerCase();
        this.bukkitSound = null;
    }

    private SoundBuilder(Sound bukkitSound) {
        this.soundKey = null;
        this.bukkitSound = bukkitSound;
    }

    public static SoundBuilder of(Sound sound) {
        return new SoundBuilder(sound);
    }

    public static SoundBuilder of(String soundKey) {
        return new SoundBuilder(soundKey);
    }

    public SoundBuilder volume(float volume) {
        this.volume = volume;
        return this;
    }

    public SoundBuilder pitch(float pitch) {
        this.pitch = pitch;
        return this;
    }

    /** Sets random pitch spread: actual pitch is {@code pitch +/- (variation / 2)}. */
    public SoundBuilder variation(float variation) {
        this.pitchVariation = variation;
        return this;
    }

    private float getFinalPitch() {
        if (pitchVariation <= 0) return pitch;
        return pitch + ((ThreadLocalRandom.current().nextFloat() - 0.5f) * pitchVariation);
    }

    private void playAt(Player player, Location location, float rolledPitch) {
        if (bukkitSound != null) {
            player.playSound(location, bukkitSound, SoundCategory.MASTER, volume, rolledPitch);
        } else if (soundKey != null) {
            player.playSound(location, soundKey, SoundCategory.MASTER, volume, rolledPitch);
        }
    }

    // --- Play Logic ---

    public void play(Player player) {
        play(player, player.getLocation());
    }

    public void play(Player player, Location location) {
        playAt(player, location, getFinalPitch());
    }

    public void play(Location location) {
        if (location.getWorld() == null) return;
        float rolledPitch = getFinalPitch();
        if (bukkitSound != null) {
            location.getWorld().playSound(location, bukkitSound, SoundCategory.MASTER, volume, rolledPitch);
        } else if (soundKey != null) {
            location.getWorld().playSound(location, soundKey, SoundCategory.MASTER, volume, rolledPitch);
        }
    }

    private void play(Collection<? extends Player> players, Location location) {
        for (Player player : players) {
            play(player, location);
        }
    }

    /** LOCAL-scope playback: sent to everyone in the world with vanilla distance attenuation. */
    public void playLocal(Location location) {
        if (location.getWorld() == null) return;
        play(location.getWorld().getPlayers(), location);
    }

    /**
     * GLOBAL-scope playback: heard map-wide by everyone in the world. Beyond vanilla range,
     * places a directional proxy {@value #DISTANT_PROXY_OFFSET_BLOCKS} blocks toward the source.
     */
    public void playEverywhere(Location location) {
        if (location.getWorld() == null) return;
        float rolledPitch = getFinalPitch();
        double range = VANILLA_AUDIBLE_RANGE_PER_VOL * Math.max(1.0f, volume);
        double rangeSq = range * range;
        for (Player player : location.getWorld().getPlayers()) {
            Location at = location;
            if (player.getLocation().distanceSquared(location) > rangeSq) {
                Vector toSource = location.toVector().subtract(player.getLocation().toVector()).normalize();
                at = player.getLocation().add(toSource.multiply(DISTANT_PROXY_OFFSET_BLOCKS));
            }
            playAt(player, at, rolledPitch);
        }
    }
}
