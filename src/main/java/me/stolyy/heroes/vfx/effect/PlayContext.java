package me.stolyy.heroes.vfx.effect;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;

/** Invocation parameters passed when playing a {@link VisualEffect}. */
public record PlayContext(
        Location at,
        @Nullable Vector direction,
        @Nullable Location end,
        OptionalDouble radiusOverride,
        @Nullable String skinId
) {
    public static PlayContext at(Location at) {
        return at(at, null);
    }

    public static PlayContext at(Location at, @Nullable String skinId) {
        return new PlayContext(at, null, null, OptionalDouble.empty(), skinId);
    }

    public static PlayContext directional(Location at, @Nullable Vector direction, @Nullable String skinId) {
        return new PlayContext(at, direction, null, OptionalDouble.empty(), skinId);
    }

    public static PlayContext sized(Location at, @Nullable Vector direction, double radiusOverride, @Nullable String skinId) {
        OptionalDouble radius = radiusOverride >= 0 ? OptionalDouble.of(radiusOverride) : OptionalDouble.empty();
        return new PlayContext(at, direction, null, radius, skinId);
    }

    public static PlayContext line(Location start, Location end, @Nullable String skinId) {
        return new PlayContext(start, null, end, OptionalDouble.empty(), skinId);
    }
}
