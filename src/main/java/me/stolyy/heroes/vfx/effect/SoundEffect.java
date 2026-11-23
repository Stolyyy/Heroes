package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.control.Controller;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A configurable sound effect that can hold one or more sub-sounds. A single yml block
 * (with `sound:`, `volume:`, etc. at the root) parses to one sub-sound; a block with a
 * `sounds:` list parses one sub-sound per list entry. All `play(...)` overloads dispatch
 * to every sub-sound, so authors can layer multiple sounds in a single yml entry.
 */
public class SoundEffect {
    /**
     * PLAYER — only the triggering player hears it, at their own position (personal cues).
     * LOCAL — played at the source location with vanilla distance attenuation; anyone close
     *         enough hears it. THE DEFAULT when a sound block has no scope key (this is the
     *         behavior the pre-rework "GLOBAL" actually had — clients attenuated it anyway).
     * GLOBAL — every player in the world hears it regardless of distance, still directional
     *          (see SoundBuilder.playEverywhere). For map-wide announcements: ult activations.
     */
    public enum Scope { GLOBAL, LOCAL, PLAYER }

    private final List<SubSound> subs = new ArrayList<>();

    public SoundEffect(ConfigurationSection config) {
        if (config == null) return;

        // Multi-form: `sounds:` list of sub-sound blocks.
        if (config.contains("sounds")) {
            List<?> list = config.getList("sounds");
            if (list != null) {
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        ConfigurationSection sub = config.createSection("temp_" + System.nanoTime(), (Map<?, ?>) obj);
                        SubSound parsed = SubSound.parse(sub);
                        if (parsed != null) subs.add(parsed);
                    }
                }
            }
            return;
        }

        // Single-form: parse the root config as one sub-sound.
        SubSound parsed = SubSound.parse(config);
        if (parsed != null) subs.add(parsed);
    }

    public void play(Player player) {
        for (SubSound s : subs) s.play(player.getLocation(), player);
    }

    public void play(Controller controller) {
        if (controller != null && controller.isPlayer()) play((Player) controller.getEntity());
    }

    public void play(Location location) {
        for (SubSound s : subs) s.play(location, null);
    }

    public void play(Location location, Controller controller) {
        if (controller == null) {
            play(location);
            return;
        }
        for (SubSound s : subs) s.playForController(location, controller);
    }

    public void play(Location location, Player context) {
        for (SubSound s : subs) s.play(location, context);
    }

    /** Holds one sound's parsed config and playback. */
    private static final class SubSound {
        private final String soundKey;
        private final float volume;
        private final float pitch;
        private final float pitchVariation;
        private final Scope scope;

        private SubSound(String soundKey, float volume, float pitch, float pitchVariation, Scope scope) {
            this.soundKey = soundKey;
            this.volume = volume;
            this.pitch = pitch;
            this.pitchVariation = pitchVariation;
            this.scope = scope;
        }

        /**
         * Every read here goes through a NO-ARG getter, because those are the only ones Bukkit
         * resolves against {@code setDefaults()} — the overloads that take a supplied default
         * return it without ever consulting the defaults tree. That distinction is load-bearing:
         * {@code getConfigurationSection()} hands back a NEW EMPTY section for a block that lives
         * only in default.yml, so the supplied-default reads made every inherited death/kill/win
         * sound parse as GLOBAL at pitch 1.0. The literal constants below are a last resort, used
         * only when a key is absent from the block AND from default.yml.
         */
        static SubSound parse(ConfigurationSection config) {
            String key = config.getString("sound");
            if (key == null) return null;
            float volume = (float) readDouble(config, "volume", 1.0);
            float pitch = (float) readDouble(config, "pitch", 1.0);
            float pitchVariation = (float) readDouble(config, "pitch_variation", 0.0);
            Object scopeValue = config.get("scope");
            Scope scope = scopeValue != null ? Scope.valueOf(scopeValue.toString().toUpperCase()) : Scope.LOCAL;
            return new SubSound(key, volume, pitch, pitchVariation, scope);
        }

        private static double readDouble(ConfigurationSection config, String path, double fallback) {
            Object value = config.get(path);
            return value instanceof Number number ? number.doubleValue() : fallback;
        }

        void play(Location location, Player context) {
            if (scope == Scope.PLAYER) {
                if (context == null) return; // location-only call site: nobody to play to
                // PLAYER scope: play at the player's own location so distance attenuation
                // doesn't silence sounds from far-away hit locations.
                SoundBuilder.of(soundKey)
                        .volume(volume)
                        .pitch(pitch)
                        .variation(pitchVariation)
                        .play(context, context.getLocation());
                return;
            }
            // LOCAL and GLOBAL both dereference the location (world lookup). A dead or
            // despawned entity resolves to a null location, and this is a dispatch path
            // that must never throw back into the damage/death handlers.
            if (location == null) return;
            SoundBuilder builder = SoundBuilder.of(soundKey)
                    .volume(volume)
                    .pitch(pitch)
                    .variation(pitchVariation);
            if (scope == Scope.LOCAL) {
                builder.playGlobal(location); // "global packet, vanilla attenuation" = LOCAL
            } else {
                builder.playEverywhere(location); // true GLOBAL: audible map-wide
            }
        }

        void playForController(Location location, Controller controller) {
            if (controller.isPlayer()) {
                play(location, (Player) controller.getEntity());
            } else if (scope != Scope.PLAYER) {
                // LOCAL/GLOBAL are location-driven — a bot's casts stay audible.
                play(location, null);
            }
            // PLAYER scope with non-player controller = no-op
        }
    }
}
