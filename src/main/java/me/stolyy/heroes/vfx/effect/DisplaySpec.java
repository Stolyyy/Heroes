package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Parsed {@code ITEM_DISPLAY} visual sub-effect. */
final class DisplaySpec implements VisualSubEffect {
    private static final int PERSISTENT_SINGLE_TELEPORT_TICKS = 1;
    private static final int PERSISTENT_SINGLE_INTERPOLATION_TICKS = 2;

    private final ModelRef model;
    private final VisualShape shape;
    private final Vector offset;
    private final float scale;
    private final ItemDisplay.ItemDisplayTransform transform;
    private final long duration;
    private final double step;
    private final double modelBackOffset;

    private DisplaySpec(ModelRef model, VisualShape shape, Vector offset, float scale,
                        ItemDisplay.ItemDisplayTransform transform, long duration,
                        double step, double modelBackOffset) {
        this.model = model;
        this.shape = shape;
        this.offset = offset;
        this.scale = scale;
        this.transform = transform;
        this.duration = duration;
        this.step = step;
        this.modelBackOffset = modelBackOffset;
    }

    static DisplaySpec parse(ConfigurationSection config, VisualShape shape, Vector offset) {
        ModelRef model = ModelRef.parse(Configs.requireSection(config, "model"));
        float scale = (float) Configs.orTemplate(config, "scale", "visual.display.scale");
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("VisualEffect scale must be > 0, got " + scale);
        }
        long duration = Configs.orTemplateLong(config, "duration", "visual.display.duration");
        if (duration <= 0) {
            throw new IllegalArgumentException("VisualEffect duration must be > 0, got " + duration);
        }
        double step = Configs.orTemplate(config, "step", "visual.display.step");
        if (!Double.isFinite(step) || step <= 0) {
            throw new IllegalArgumentException("VisualEffect step must be > 0, got " + step);
        }
        double modelBackOffset = Configs.orTemplate(config, "model_back_offset", "visual.display.model_back_offset");
        String transformStr = Configs.orTemplateString(config, "transform", "visual.display.transform").toUpperCase();
        ItemDisplay.ItemDisplayTransform transform = ItemDisplay.ItemDisplayTransform.valueOf(transformStr);

        return new DisplaySpec(model, shape, offset, scale, transform, duration, step, modelBackOffset);
    }

    private ItemDisplayBuilder baseBuilder(Location at, @Nullable String liveSkin) {
        return ItemDisplayBuilder.create(at)
                .modelData(model, liveSkin)
                .scale(scale)
                .transformType(transform);
    }

    @Override
    public void play(PlayContext ctx) {
        Location target = ctx.at().clone().add(offset);
        Vector lineVec = ctx.end() != null ? ctx.end().toVector().subtract(target.toVector()) : null;
        spawnAll(target, lineVec, ctx.direction(), duration, ctx.skinId(), null);
    }

    @Override
    public boolean canSpawnDisplay() {
        return true;
    }

    @Override
    public ItemDisplay spawnPersistentSingle(Location location, @Nullable String liveSkin) {
        return baseBuilder(location.clone().add(offset), liveSkin)
                .smooth(PERSISTENT_SINGLE_TELEPORT_TICKS, PERSISTENT_SINGLE_INTERPOLATION_TICKS)
                .spawn();
    }

    @Override
    public void spawnPersistentAll(Location location, @Nullable Vector direction,
                                   List<ItemDisplay> sink, @Nullable String liveSkin) {
        spawnAll(location.clone().add(offset), direction, direction, 0L, liveSkin, sink);
    }

    @Override
    public double modelBackOffset() {
        return modelBackOffset;
    }

    private void spawnAll(Location target, @Nullable Vector lineVec, @Nullable Vector orientDir,
                          long lifetimeTicks, @Nullable String liveSkin, @Nullable List<ItemDisplay> sink) {
        if (shape == VisualShape.LINE && lineVec != null) {
            Vector lineDir = lineVec.clone();
            double distance = lineDir.length();
            if (distance < 0.01) return;
            lineDir.normalize();
            for (double d = 0; d <= distance; d += step) {
                Location loc = target.clone().add(lineDir.clone().multiply(d));
                ItemDisplayBuilder builder = baseBuilder(loc, liveSkin)
                        .oriented(lineDir, modelBackOffset);
                if (lifetimeTicks > 0) builder.lifetime(lifetimeTicks);
                ItemDisplay spawned = builder.spawn();
                if (sink != null) sink.add(spawned);
            }
            return;
        }

        ItemDisplayBuilder builder = baseBuilder(target, liveSkin);
        if (orientDir != null) builder.oriented(orientDir, modelBackOffset);
        if (lifetimeTicks > 0) builder.lifetime(lifetimeTicks);
        ItemDisplay spawned = builder.spawn();
        if (sink != null) sink.add(spawned);
    }
}
