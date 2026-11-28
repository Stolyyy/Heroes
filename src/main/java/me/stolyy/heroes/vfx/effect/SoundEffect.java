package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.SpecList;
import me.stolyy.heroes.util.TemplateConfig;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A configurable sound effect that can hold one or more sub-sounds. A single YML block parses to
 * one sub-sound; a {@code sounds:} list parses one sub-sound per entry. All {@code play(...)}
 * overloads dispatch to every sub-sound.
 */
public class SoundEffect {
    /** Explicit no-op sound for genuinely optional sound hooks. */
    public static final SoundEffect NONE = new SoundEffect();

    /**
     * PLAYER — only the triggering player hears it, at their own position (personal cues).
     * LOCAL  — played at the source location with vanilla distance attenuation (default).
     * GLOBAL — every player in the world hears it regardless of distance, directional (ult cues).
     */
    public enum Scope { GLOBAL, LOCAL, PLAYER }

    private final List<SubSound> subs;

    private SoundEffect() {
        this.subs = List.of();
    }

    /**
     * Reads an optional sound section at {@code path} under {@code parent}. Returns {@link #NONE}
     * when {@code path} is absent, and throws loudly when present but malformed.
     */
    public static SoundEffect optional(ConfigurationSection parent, String path) {
        if (parent == null || !parent.contains(path)) return NONE;
        ConfigurationSection section = Configs.requireSection(parent, path);
        if (section.getKeys(false).isEmpty()) return NONE;
        return new SoundEffect(section);
    }

    public SoundEffect(ConfigurationSection config) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "SoundEffect config section cannot be null (use SoundEffect.optional for optional cues)");
        }
        this.subs = SpecList.parse(config, "sounds", SubSound::parse);
    }

    public boolean isEmpty() {
        return subs.isEmpty();
    }

    public void play(Player player) {
        for (SubSound s : subs) s.play(player.getLocation(), player);
    }

    /** Plays directly to {@code player} at their own position regardless of configured scope (non-positional). */
    public void playPersonal(Player player) {
        for (SubSound s : subs) s.playPersonal(player);
    }

    public void play(Controller controller) {
        if (controller != null) play(controller.getLocation(), controller);
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
         * Reads through Bukkit's no-arg getters first so {@code default.yml} inheritance via
         * {@code setDefaults()} resolves before falling back to {@code templates.yml} {@code sound.*}.
         */
        static SubSound parse(ConfigurationSection config) {
            String key = Configs.requireString(config, "sound");
            float volume = (float) readDoubleOrTemplate(config, "volume", "sound.volume");
            float pitch = (float) readDoubleOrTemplate(config, "pitch", "sound.pitch");
            float pitchVariation = (float) readDoubleOrTemplate(config, "pitch_variation", "sound.pitch_variation");
            Object scopeValue = config.get("scope");
            String scopeStr = scopeValue != null
                    ? scopeValue.toString()
                    : TemplateConfig.requireString("sound.scope");
            Scope scope = Scope.valueOf(scopeStr.toUpperCase());
            return new SubSound(key, volume, pitch, pitchVariation, scope);
        }

        private static double readDoubleOrTemplate(ConfigurationSection config, String path, String templatePath) {
            Object value = config.get(path);
            if (value == null) return TemplateConfig.requireDouble(templatePath);
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("SoundEffect '" + path + "' must be a number, got: " + value);
            }
            return number.doubleValue();
        }

        void play(Location location, Player context) {
            if (scope == Scope.PLAYER) {
                if (context == null) return; // location-only call site: nobody to play to
                SoundBuilder.of(soundKey)
                        .volume(volume)
                        .pitch(pitch)
                        .variation(pitchVariation)
                        .play(context, context.getLocation());
                return;
            }
            // Dead/despawned entities resolve to null location; avoid throwing inside death handlers.
            if (location == null) return;
            SoundBuilder builder = SoundBuilder.of(soundKey)
                    .volume(volume)
                    .pitch(pitch)
                    .variation(pitchVariation);
            if (scope == Scope.LOCAL) {
                builder.playLocal(location);
            } else {
                builder.playEverywhere(location);
            }
        }

        void playPersonal(Player player) {
            SoundBuilder.of(soundKey)
                    .volume(volume)
                    .pitch(pitch)
                    .variation(pitchVariation)
                    .play(player, player.getLocation());
        }

        void playForController(Location location, Controller controller) {
            if (controller.isPlayer()) {
                play(location, (Player) controller.getEntity());
            } else if (scope != Scope.PLAYER) {
                play(location, null);
            }
        }
    }
}
