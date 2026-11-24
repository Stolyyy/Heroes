package me.stolyy.heroes.vfx.effect;

import org.bukkit.Location;

/** Geometric emission shapes supported by {@link VisualEffect}. */
public enum VisualShape {
    POINT {
        @Override
        public void draw(ParticleBuilder builder, Location target, PlayContext ctx,
                         double defaultRadius, int points, double step) {
            if (ctx.direction() != null) builder.spawn(target, ctx.direction());
            else builder.spawn(target);
        }
    },
    RING {
        @Override
        public void draw(ParticleBuilder builder, Location target, PlayContext ctx,
                         double defaultRadius, int points, double step) {
            builder.ring(target, ctx.radiusOverride().orElse(defaultRadius), points);
        }
    },
    ORIENTED_RING {
        @Override
        public void draw(ParticleBuilder builder, Location target, PlayContext ctx,
                         double defaultRadius, int points, double step) {
            double radius = ctx.radiusOverride().orElse(defaultRadius);
            if (ctx.direction() != null) builder.orientedRing(target, ctx.direction(), radius, points);
            else builder.ring(target, radius, points);
        }
    },
    SPHERE {
        @Override
        public void draw(ParticleBuilder builder, Location target, PlayContext ctx,
                         double defaultRadius, int points, double step) {
            builder.sphere(target, ctx.radiusOverride().orElse(defaultRadius), points);
        }
    },
    LINE {
        @Override
        public void draw(ParticleBuilder builder, Location target, PlayContext ctx,
                         double defaultRadius, int points, double step) {
            if (ctx.end() != null) {
                builder.line(target, ctx.end(), step);
            } else if (ctx.direction() != null) {
                double length = ctx.radiusOverride().orElse(defaultRadius);
                Location end = target.clone().add(ctx.direction().clone().multiply(length));
                builder.line(target, end, step);
            } else {
                builder.spawn(target);
            }
        }
    };

    public abstract void draw(ParticleBuilder builder, Location target, PlayContext ctx,
                              double defaultRadius, int points, double step);
}
