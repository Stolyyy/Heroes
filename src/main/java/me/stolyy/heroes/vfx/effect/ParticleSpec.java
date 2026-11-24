package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.util.ColorParser;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.SpecList;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Parsed particle visual sub-effect with a pre-constructed {@link ParticleBuilder}. */
final class ParticleSpec implements VisualSubEffect {
    private final VisualShape shape;
    private final Vector offset;
    private final ParticleBuilder builder;
    private final double radius;
    private final int points;
    private final double step;

    private ParticleSpec(VisualShape shape, Vector offset, ParticleBuilder builder,
                         double radius, int points, double step) {
        this.shape = shape;
        this.offset = offset;
        this.builder = builder;
        this.radius = radius;
        this.points = points;
        this.step = step;
    }

    static ParticleSpec parse(ConfigurationSection config, VisualShape shape, Vector offset) {
        List<ParticleBuilderEntry> entries = SpecList.parse(config, "particles", ParticleBuilderEntry::parse);
        if (entries.isEmpty()) {
            throw new IllegalStateException("VisualEffect 'particles' at '" + config.getCurrentPath() + "' cannot be empty");
        }

        ParticleBuilder builder = ParticleBuilder.create();
        for (ParticleBuilderEntry entry : entries) {
            entry.appendTo(builder);
        }

        double radius = Configs.orTemplate(config, "radius", "visual.particle.radius");
        int points = Configs.orTemplateInt(config, "points", "visual.particle.points");
        if (points <= 0) {
            throw new IllegalArgumentException("VisualEffect points must be > 0, got " + points);
        }
        double step = Configs.orTemplate(config, "step", "visual.particle.step");
        if (!Double.isFinite(step) || step <= 0) {
            throw new IllegalArgumentException("VisualEffect step must be > 0, got " + step);
        }

        return new ParticleSpec(shape, offset, builder, radius, points, step);
    }

    @Override
    public void play(PlayContext ctx) {
        Location target = ctx.at().clone().add(offset);
        shape.draw(builder, target, ctx, radius, points, step);
    }

    @Override
    public boolean canSpawnDisplay() {
        return false;
    }

    @Override
    public @Nullable ItemDisplay spawnPersistentSingle(Location location, @Nullable String liveSkin) {
        return null;
    }

    @Override
    public void spawnPersistentAll(Location location, @Nullable Vector direction,
                                   List<ItemDisplay> sink, @Nullable String liveSkin) {
        play(PlayContext.directional(location, direction, liveSkin));
    }

    @Override
    public double modelBackOffset() {
        return 0;
    }

    private record ParticleBuilderEntry(
            Particle particle,
            int count,
            double extra,
            double spread,
            boolean directional,
            @Nullable Color color,
            @Nullable Color toColor,
            float size,
            @Nullable BlockData blockData
    ) {
        static ParticleBuilderEntry parse(ConfigurationSection pConfig) {
            Particle particle = Particle.valueOf(Configs.requireString(pConfig, "particle").toUpperCase());
            int count = Configs.orTemplateInt(pConfig, "count", "visual.particle.count");
            double extra = pConfig.contains("extra")
                    ? Configs.requireDouble(pConfig, "extra")
                    : Configs.orTemplate(pConfig, "speed", "visual.particle.extra");
            double spread = Configs.orTemplate(pConfig, "spread", "visual.particle.spread");
            boolean directional = pConfig.getBoolean("directional", false);

            Color color = pConfig.contains("color")
                    ? ColorParser.parse(Configs.requireString(pConfig, "color"))
                    : null;
            Color toColor = pConfig.contains("to_color")
                    ? ColorParser.parse(Configs.requireString(pConfig, "to_color"))
                    : null;
            float size = (float) Configs.orTemplate(pConfig, "size", "visual.particle.size");

            BlockData blockData = null;
            if (pConfig.contains("material")) {
                String matName = Configs.requireString(pConfig, "material");
                Material material = Material.matchMaterial(matName);
                if (material == null || !material.isBlock()) {
                    throw new IllegalArgumentException("Invalid or non-block particle material '" + matName + "'");
                }
                blockData = material.createBlockData();
            }

            return new ParticleBuilderEntry(particle, count, extra, spread, directional, color, toColor, size, blockData);
        }

        void appendTo(ParticleBuilder builder) {
            builder.add(particle)
                    .count(count)
                    .extra(extra)
                    .offset(spread, spread, spread);

            if (directional) {
                builder.directional();
            }

            if (particle == Particle.DUST && color != null) {
                builder.color(color, size);
            } else if (particle == Particle.DUST_COLOR_TRANSITION && color != null && toColor != null) {
                builder.transition(color, toColor, size);
            } else if (blockData != null) {
                builder.block(blockData);
            }
        }
    }
}
