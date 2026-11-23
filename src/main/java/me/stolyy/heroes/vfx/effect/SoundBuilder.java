package me.stolyy.heroes.vfx.effect;

import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

public class SoundBuilder {

    private final String soundKey;
    private final org.bukkit.Sound bukkitSound;
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private float pitchVariation = 0.0f;
    private Sound.Source source = Sound.Source.MASTER;

    private SoundBuilder(String soundKey) {
        this.soundKey = soundKey.toLowerCase();
        this.bukkitSound = null;
    }

    private SoundBuilder(org.bukkit.Sound bukkitSound) {
        this.soundKey = null;
        this.bukkitSound = bukkitSound;
    }

    public static SoundBuilder of(org.bukkit.Sound sound) {
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

    /**
     * Adds a random variation to the pitch when played.
     * The actual pitch will be: pitch +/- (variation / 2)
     */
    public SoundBuilder variation(float variation) {
        this.pitchVariation = variation;
        return this;
    }

    public SoundBuilder source(Sound.Source source) {
        this.source = source;
        return this;
    }

    private float getFinalPitch() {
        if (pitchVariation <= 0) return pitch;
        return pitch + ((ThreadLocalRandom.current().nextFloat() - 0.5f) * pitchVariation);
    }

    // --- Play Logic ---

    public void play(Player player) {
        play(player, player.getLocation());
    }

    public void play(Player player, Location location) {
        if (bukkitSound != null) {
            player.playSound(location, bukkitSound, org.bukkit.SoundCategory.valueOf(source.name()), volume, getFinalPitch());
        } else if (soundKey != null) {
            player.playSound(location, soundKey, org.bukkit.SoundCategory.valueOf(source.name()), volume, getFinalPitch());
        }
    }

    public void play(Location location) {
        if (location.getWorld() == null) return;
        if (bukkitSound != null) {
            location.getWorld().playSound(location, bukkitSound, org.bukkit.SoundCategory.valueOf(source.name()), volume, getFinalPitch());
        } else if (soundKey != null) {
            location.getWorld().playSound(location, soundKey, org.bukkit.SoundCategory.valueOf(source.name()), volume, getFinalPitch());
        }
    }

    public void play(Collection<? extends Player> players, Location location) {
        for (Player player : players) {
            play(player, location);
        }
    }

    /** LOCAL-scope playback: the packet goes to everyone in the world, but the CLIENT applies
     *  vanilla distance attenuation (rolloff ≈ 16 x volume blocks) — distant players hear
     *  nothing. (Named before the scope rework; this is what Scope.LOCAL dispatches to.) */
    public void playGlobal(Location location) {
        if (location.getWorld() == null) return;
        play(location.getWorld().getPlayers(), location);
    }

    /**
     * GLOBAL-scope playback: every player in the world hears it, wherever they are. Within
     * vanilla audible range (16 x volume blocks) it plays at the source — real directionality
     * and natural attenuation. Beyond that, it plays at a point 8 blocks from the listener
     * TOWARD the source: still at configured volume, still directionally from the right way,
     * just no longer distance-faded — the point of GLOBAL (map-wide ult announcements).
     * One pitch roll shared by every listener, so they all hear the same sound.
     */
    public void playEverywhere(Location location) {
        if (location.getWorld() == null) return;
        float rolledPitch = getFinalPitch();
        double range = 16.0 * Math.max(1.0f, volume);
        double rangeSq = range * range;
        for (Player player : location.getWorld().getPlayers()) {
            Location at = location;
            if (player.getLocation().distanceSquared(location) > rangeSq) {
                org.bukkit.util.Vector toSource = location.toVector()
                        .subtract(player.getLocation().toVector()).normalize();
                at = player.getLocation().add(toSource.multiply(8));
            }
            if (bukkitSound != null) {
                player.playSound(at, bukkitSound, org.bukkit.SoundCategory.valueOf(source.name()), volume, rolledPitch);
            } else if (soundKey != null) {
                player.playSound(at, soundKey, org.bukkit.SoundCategory.valueOf(source.name()), volume, rolledPitch);
            }
        }
    }
}
