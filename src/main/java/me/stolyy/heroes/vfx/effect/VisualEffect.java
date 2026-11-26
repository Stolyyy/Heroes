package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.SpecList;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A configurable visual effect holding one or more sub-effects (particles or item displays).
 * A single YML block parses one sub-effect; an {@code effects:} list parses one sub-effect per entry.
 */
public class VisualEffect {
    /** Explicit no-op visual for genuinely optional visual hooks. */
    public static final VisualEffect NONE = new VisualEffect();

    private enum Type { PARTICLE, ITEM_DISPLAY, NONE }

    private final List<VisualSubEffect> subs = new ArrayList<>();

    private VisualEffect() {}

    /**
     * Reads an optional visual section at {@code path} under {@code parent}. Returns {@link #NONE}
     * when {@code path} is absent, and throws loudly when present but malformed.
     */
    public static VisualEffect optional(ConfigurationSection parent, String path) {
        if (parent == null || !parent.contains(path)) return NONE;
        ConfigurationSection section = Configs.requireSection(parent, path);
        if (section.getKeys(false).isEmpty()) return NONE;
        return new VisualEffect(section);
    }

    public VisualEffect(ConfigurationSection config) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "VisualEffect config section cannot be null (use VisualEffect.optional for optional visuals)");
        }
        for (VisualSubEffect parsed : SpecList.parse(config, "effects", VisualEffect::parseSub)) {
            if (parsed != null) subs.add(parsed);
        }
    }

    private static @Nullable VisualSubEffect parseSub(ConfigurationSection sub) {
        Type type = Type.valueOf(Configs.requireString(sub, "type").toUpperCase());
        if (type == Type.NONE) return null;

        VisualShape shape = VisualShape.valueOf(
                Configs.orTemplateString(sub, "shape", "visual.shape").toUpperCase());
        Vector offset = new Vector(
                Configs.orTemplate(sub, "offset.x", "visual.offset.x"),
                Configs.orTemplate(sub, "offset.y", "visual.offset.y"),
                Configs.orTemplate(sub, "offset.z", "visual.offset.z")
        );

        return switch (type) {
            case PARTICLE -> ParticleSpec.parse(sub, shape, offset);
            case ITEM_DISPLAY -> DisplaySpec.parse(sub, shape, offset);
            case NONE -> null;
        };
    }

    public void play(PlayContext ctx) {
        for (VisualSubEffect sub : subs) {
            sub.play(ctx);
        }
    }

    public void play(Location location) {
        play(PlayContext.at(location));
    }

    public void play(Location location, String skinId) {
        play(PlayContext.at(location, skinId));
    }

    public void play(Location location, Vector direction) {
        play(PlayContext.directional(location, direction, null));
    }

    public void play(Location location, Vector direction, String skinId) {
        play(PlayContext.directional(location, direction, skinId));
    }

    public void play(Location location, Vector direction, double radiusOverride) {
        play(PlayContext.sized(location, direction, radiusOverride, null));
    }

    public void play(Location location, Vector direction, double radiusOverride, String skinId) {
        play(PlayContext.sized(location, direction, radiusOverride, skinId));
    }

    public void play(Location start, Location end) {
        play(PlayContext.line(start, end, null));
    }

    public void play(Location start, Location end, String skinId) {
        play(PlayContext.line(start, end, skinId));
    }

    public void playAlong(Location start, Vector direction, double length, double spacing, String skinId) {
        playAlong(start, direction, length, spacing, skinId, 0.0);
    }

    /** Samples this visual evenly along a ray starting at {@code startOffset} up to {@code length}. */
    public void playAlong(Location start, Vector direction, double length, double spacing,
                          String skinId, double startOffset) {
        if (!Double.isFinite(spacing) || spacing <= 0) {
            throw new IllegalArgumentException("VisualEffect.playAlong spacing must be > 0, got " + spacing);
        }
        Vector dir = direction.clone().normalize();
        for (double d = startOffset; d <= length; d += spacing) {
            play(start.clone().add(dir.clone().multiply(d)), dir, skinId);
        }
    }

    /**
     * Spawns a persistent ItemDisplay at the given location (no auto-removal). The caller owns the
     * returned display and is responsible for removing it. Other sub-effects play one-shot alongside.
     * Returns {@code null} if no sub-effect is an {@code ITEM_DISPLAY}.
     */
    public @Nullable ItemDisplay spawnDisplay(Location location) {
        return spawnDisplay(location, null);
    }

    public @Nullable ItemDisplay spawnDisplay(Location location, @Nullable String skinId) {
        ItemDisplay primary = null;
        PlayContext oneShot = PlayContext.at(location, skinId);
        for (VisualSubEffect sub : subs) {
            if (primary == null && sub.canSpawnDisplay()) {
                primary = sub.spawnPersistentSingle(location, skinId);
            } else {
                sub.play(oneShot);
            }
        }
        return primary;
    }

    /**
     * Spawns ALL persistent displays produced by this effect (no auto-removal); PARTICLE sub-effects
     * play one-shot alongside. The caller owns all returned displays and must remove them on cleanup.
     */
    public List<ItemDisplay> playPersistent(Location location, Vector direction) {
        return playPersistent(location, direction, null);
    }

    public List<ItemDisplay> playPersistent(Location location, Vector direction, @Nullable String skinId) {
        List<ItemDisplay> spawned = new ArrayList<>();
        for (VisualSubEffect sub : subs) {
            sub.spawnPersistentAll(location, direction, spawned, skinId);
        }
        return spawned;
    }

    /**
     * The {@code model_back_offset} of the sub-effect {@link #spawnDisplay} returns, in blocks.
     * Callers that re-aim that display themselves pass this to {@link AbstractDisplayBuilder#orient}.
     */
    public double modelBackOffset() {
        for (VisualSubEffect sub : subs) {
            if (sub.canSpawnDisplay()) return sub.modelBackOffset();
        }
        return 0;
    }
}
