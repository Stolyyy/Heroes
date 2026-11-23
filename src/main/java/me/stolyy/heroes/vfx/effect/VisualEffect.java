package me.stolyy.heroes.vfx.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A configurable visual effect that can hold one or more sub-effects (particles, item
 * displays, or combinations). Single-block yml parses to one sub-effect; a block with an
 * `effects:` list parses one sub-effect per list entry. All `play(...)` overloads dispatch
 * to every sub-effect, so authors can layer e.g. a particle ring around an item-display
 * body in one yml entry.
 *
 * <p>Sub-effect type/shape are determined per entry. The list pattern mirrors how
 * `particles:` already accepts either a single particle at the root or a list of sub-particles
 * inside one effect; this class extends that pattern up one level.
 */
public class VisualEffect {
    private enum Type { PARTICLE, ITEM_DISPLAY, BLOCK_DISPLAY, NONE }
    private enum Shape { POINT, RING, ORIENTED_RING, SPHERE, LINE }

    private final List<SubEffect> subs = new ArrayList<>();

    public VisualEffect(ConfigurationSection config) {
        if (config == null) return;

        // Multi-form: `effects:` list of sub-effect blocks.
        if (config.contains("effects")) {
            List<?> list = config.getList("effects");
            if (list != null) {
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        ConfigurationSection sub = config.createSection("temp_" + System.nanoTime(), (Map<?, ?>) obj);
                        SubEffect parsed = SubEffect.parse(sub);
                        if (parsed != null) subs.add(parsed);
                    }
                }
            }
            return;
        }

        // Single-form: parse the root config as one sub-effect.
        SubEffect parsed = SubEffect.parse(config);
        if (parsed != null) subs.add(parsed);
    }

    public void play(Location location) {
        play(location, (String) null);
    }

    public void play(Location location, String skinId) {
        for (SubEffect s : subs) s.play(location, null, -1, null, skinId);
    }

    public void play(Location location, Vector direction) {
        play(location, direction, (String) null);
    }

    public void play(Location location, Vector direction, String skinId) {
        for (SubEffect s : subs) s.play(location, direction, -1, null, skinId);
    }

    public void play(Location location, Vector direction, double radiusOverride) {
        play(location, direction, radiusOverride, null);
    }

    public void play(Location location, Vector direction, double radiusOverride, String skinId) {
        for (SubEffect s : subs) s.play(location, direction, radiusOverride, null, skinId);
    }

    public void play(Location start, Location end) {
        play(start, end, (String) null);
    }

    public void play(Location start, Location end, String skinId) {
        for (SubEffect s : subs) s.play(start, null, -1, end, skinId);
    }

    /**
     * Spawns a persistent ItemDisplay at the given location (no auto-removal). The caller
     * owns the returned display and is responsible for removing it.
     *
     * <p>Iterates sub-effects: the FIRST {@code ITEM_DISPLAY} sub returns a single persistent
     * display (regardless of its shape configuration); all other sub-effects play one-shot
     * alongside (particles emit, line-shape displays auto-remove via their {@code duration}).
     * Returns {@code null} if no sub-effect is an {@code ITEM_DISPLAY}.
     */
    public ItemDisplay spawnDisplay(Location location) {
        return spawnDisplay(location, null);
    }

    public ItemDisplay spawnDisplay(Location location, String skinId) {
        ItemDisplay primary = null;
        for (SubEffect sub : subs) {
            if (primary == null && sub.canSpawnDisplay()) {
                primary = sub.spawnPersistentSingle(location, skinId);
            } else {
                sub.play(location, null, -1, null, skinId);
            }
        }
        return primary;
    }

    /**
     * Spawns ALL persistent displays produced by this effect (no auto-removal). LINE-shape
     * sub-effects spawn multiple displays. PARTICLE sub-effects play as one-shot alongside.
     * The caller owns all returned displays and must remove them on cleanup.
     *
     * <p>Used for projectile-bound trails where the trail emissions should live as long as
     * the projectile lives, then despawn together.
     */
    public List<ItemDisplay> playPersistent(Location location, Vector direction) {
        return playPersistent(location, direction, null);
    }

    public List<ItemDisplay> playPersistent(Location location, Vector direction, String skinId) {
        List<ItemDisplay> spawned = new ArrayList<>();
        for (SubEffect sub : subs) {
            sub.spawnPersistentAll(location, direction, spawned, skinId);
        }
        return spawned;
    }

    /**
     * The {@code model_back_offset} of the sub-effect {@link #spawnDisplay} returns, in blocks.
     * Callers that re-aim that display themselves must pass this to
     * {@link AbstractDisplayBuilder#orient} so the offset is recomputed as the heading changes.
     */
    public double modelBackOffset() {
        for (SubEffect sub : subs) {
            if (sub.canSpawnDisplay()) return sub.modelBackOffset;
        }
        return 0;
    }

    // ---------- shared parse helpers ----------

    private static Particle parseParticle(String key) {
        if (key == null) return null;
        try { return Particle.valueOf(key.toUpperCase()); } catch (Exception e) { return null; }
    }

    private static Color parseColor(String str) {
        if (str == null) return Color.RED;

        // 1. Try Hex
        if (str.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(str.substring(1), 16);
                return Color.fromRGB(rgb);
            } catch (NumberFormatException ignored) {}
        }

        // 2. Try RGB "255,0,0"
        if (str.contains(",")) {
            String[] parts = str.split(",");
            if (parts.length == 3) {
                try {
                    return Color.fromRGB(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())
                    );
                } catch (Exception ignored) {}
            }
        }

        // 3. Try Standard Fields (RED, BLUE, etc.)
        try {
            Field field = Color.class.getField(str.toUpperCase());
            return (Color) field.get(null);
        } catch (Exception ignored) {}

        return Color.RED; // Fallback
    }

    private static class ParticleConfig {
        Particle particle;
        int count;
        double extra; // speed
        double spread;
        boolean directional;

        // Dust / Transition
        Color color;
        Color toColor;
        float size;
        // Block-data particles (BLOCK, BLOCK_CRUMBLE, FALLING_DUST, DUST_PILLAR...): these
        // REQUIRE a BlockData payload — spawning one without it throws in CraftParticle.
        org.bukkit.block.data.BlockData blockData;
    }

    /**
     * One configured visual sub-effect. Holds the parsed type/shape/particles/model etc. and
     * implements the actual playback. The outer {@link VisualEffect} fans calls out to each.
     */
    private static final class SubEffect {
        private Type type;
        private Shape shape;
        private Vector offset;

        // Shape Specific
        private double radius;
        private int points;
        private double step;

        // Multiple Particles Config
        private final List<ParticleConfig> particles = new ArrayList<>();

        // Display Specific
        private String[] modelData; // hero, texture, skin
        private float scale;
        private ItemDisplay.ItemDisplayTransform transform;
        private long duration;
        private double modelBackOffset;

        static SubEffect parse(ConfigurationSection config) {
            if (config == null) return null;
            SubEffect e = new SubEffect();
            e.parseTypeAndShape(config);
            e.offset = new Vector(
                    config.getDouble("offset.x", 0),
                    config.getDouble("offset.y", 0),
                    config.getDouble("offset.z", 0)
            );

            if (e.type == Type.PARTICLE) {
                e.parseParticleConfig(config);
            } else if (e.type == Type.ITEM_DISPLAY) {
                e.parseDisplayConfig(config);
            }
            return e;
        }

        private void parseTypeAndShape(ConfigurationSection config) {
            String typeStr = config.getString("type", "PARTICLE").toUpperCase();
            try {
                this.type = Type.valueOf(typeStr);
            } catch (IllegalArgumentException ex) {
                // Try to infer
                if (config.contains("particle") || config.contains("particles")) {
                    this.type = Type.PARTICLE;
                } else if (config.contains("model")) {
                    this.type = Type.ITEM_DISPLAY;
                } else {
                    this.type = Type.NONE;
                }
            }

            String shapeStr = config.getString("shape", "POINT").toUpperCase();
            try {
                this.shape = Shape.valueOf(shapeStr);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid shape: " + shapeStr);
            }
        }

        private void parseParticleConfig(ConfigurationSection config) {
            // Check for list of particles
            List<ConfigurationSection> rawConfigs = new ArrayList<>();

            if (config.contains("particles")) {
                List<?> list = config.getList("particles");
                if (list != null) {
                    for (Object obj : list) {
                        if (obj instanceof Map) {
                            ConfigurationSection pConfig = config.createSection("temp_" + System.nanoTime(), (Map<?, ?>) obj);
                            rawConfigs.add(pConfig);
                        }
                    }
                }
            } else {
                // Single particle at root
                rawConfigs.add(config);
            }

            for (ConfigurationSection pConfig : rawConfigs) {
                Particle p = parseParticle(pConfig.getString("particle"));
                if (p == null) continue;

                ParticleConfig pc = new ParticleConfig();
                pc.particle = p;
                pc.count = pConfig.getInt("count", 1);
                pc.extra = pConfig.getDouble("extra", pConfig.getDouble("speed", 0.0));
                pc.spread = pConfig.getDouble("spread", 0.0);
                pc.directional = pConfig.getBoolean("directional", false);

                if (pConfig.contains("color")) {
                    pc.color = parseColor(pConfig.getString("color"));
                    pc.size = (float) pConfig.getDouble("size", 1.0);
                }
                if (pConfig.contains("to_color")) {
                    pc.toColor = parseColor(pConfig.getString("to_color"));
                }
                if (pConfig.contains("material")) {
                    org.bukkit.Material material = org.bukkit.Material.matchMaterial(pConfig.getString("material", ""));
                    if (material != null && material.isBlock()) pc.blockData = material.createBlockData();
                }

                this.particles.add(pc);
            }

            // Shape Data
            this.radius = config.getDouble("radius", 1.0);
            this.points = config.getInt("points", 12);
            this.step = config.getDouble("step", 0.5);
        }

        private void parseDisplayConfig(ConfigurationSection config) {
            if (config.isConfigurationSection("model")) {
                ConfigurationSection modelSec = config.getConfigurationSection("model");
                // skin: an explicit yml value pins the skin; absent -> resolved from the
                // owning hero's CURRENT skin at play/spawn time (falls back to "default").
                this.modelData = new String[] {
                        modelSec.getString("hero"),
                        modelSec.getString("texture"),
                        modelSec.getString("skin")
                };
            }
            this.scale = (float) config.getDouble("scale", 1.0);
            this.duration = config.getLong("duration", 20);
            this.step = config.getDouble("step", 0.5);
            // Blocks from the model's pivot back to its rear face along AbstractDisplayBuilder's
            // MODEL_FORWARD axis. 0 genuinely means "no offset" — the model straddles the spawn
            // point, which is what every effect did before the key existed — so it defaults.
            this.modelBackOffset = config.getDouble("model_back_offset", 0.0);
            try {
                this.transform = ItemDisplay.ItemDisplayTransform.valueOf(config.getString("transform", "HEAD").toUpperCase());
            } catch (Exception ex) {
                this.transform = ItemDisplay.ItemDisplayTransform.HEAD;
            }
        }

        boolean canSpawnDisplay() {
            return type == Type.ITEM_DISPLAY && modelData != null;
        }

        /** Yml-pinned skin wins; else the caller-supplied live skin; else "default". */
        private String resolveSkin(String liveSkin) {
            if (modelData[2] != null) return modelData[2];
            return liveSkin != null ? liveSkin : "default";
        }

        /** Fire-and-forget play of this sub-effect (auto-removes display entities via duration). */
        void play(Location location, Vector direction, double radiusOverride, Location endLocation, String liveSkin) {
            Location target = location.clone().add(offset);
            switch (type) {
                case PARTICLE:
                    playParticleEffect(target, direction, radiusOverride, endLocation);
                    break;
                case ITEM_DISPLAY:
                    playDisplayEffect(target, direction, endLocation, liveSkin);
                    break;
                default:
                    break;
            }
        }

        /** Spawns a single persistent ItemDisplay (no auto-remove). Returns null if not ITEM_DISPLAY. */
        ItemDisplay spawnPersistentSingle(Location location, String liveSkin) {
            if (!canSpawnDisplay()) return null;
            Location target = location.clone().add(offset);
            return ItemDisplayBuilder.create(target)
                    .modelData(modelData[0], modelData[1], resolveSkin(liveSkin))
                    .scale(scale)
                    .transformType(transform)
                    .interpolationDuration(2)
                    .spawn();
        }

        /**
         * Spawns ALL persistent displays this sub-effect produces (no auto-remove); appends
         * them to {@code sink}. LINE-shape displays produce multiple; POINT produces one.
         * PARTICLE sub-effects play one-shot.
         */
        void spawnPersistentAll(Location location, Vector direction, List<ItemDisplay> sink, String liveSkin) {
            Location target = location.clone().add(offset);
            if (type == Type.ITEM_DISPLAY) {
                if (modelData == null) return;
                if (shape == Shape.LINE && direction != null) {
                    Vector lineDir = direction.clone();
                    double distance = lineDir.length();
                    if (distance < 0.01) return;
                    lineDir.normalize();
                    for (double d = 0; d <= distance; d += step) {
                        Location loc = target.clone().add(lineDir.clone().multiply(d));
                        ItemDisplay display = ItemDisplayBuilder.create(loc)
                                .modelData(modelData[0], modelData[1], resolveSkin(liveSkin))
                                .scale(scale)
                                .transformType(transform)
                                .oriented(lineDir, modelBackOffset)
                                .spawn();
                        sink.add(display);
                    }
                } else {
                    ItemDisplayBuilder iBuilder = ItemDisplayBuilder.create(target)
                            .modelData(modelData[0], modelData[1], resolveSkin(liveSkin))
                            .scale(scale)
                            .transformType(transform);
                    if (direction != null) iBuilder.oriented(direction, modelBackOffset);
                    sink.add(iBuilder.spawn());
                }
            } else if (type == Type.PARTICLE) {
                playParticleEffect(target, direction, -1, null);
            }
        }

        private void playParticleEffect(Location target, Vector direction, double radiusOverride, Location endLocation) {
            if (particles.isEmpty()) return;

            ParticleBuilder builder = ParticleBuilder.create();

            for (ParticleConfig pc : particles) {
                builder.add(pc.particle)
                       .count(pc.count)
                       .extra(pc.extra)
                       .offset(pc.spread, pc.spread, pc.spread);

                if (pc.directional) {
                    builder.directional();
                }

                if (pc.particle == Particle.DUST && pc.color != null) {
                    builder.color(pc.color, pc.size);
                } else if (pc.particle == Particle.DUST_COLOR_TRANSITION && pc.color != null && pc.toColor != null) {
                    builder.transition(pc.color, pc.toColor, pc.size);
                } else if (pc.blockData != null) {
                    builder.block(pc.blockData);
                }
            }

            double finalRadius = (radiusOverride >= 0) ? radiusOverride : radius;

            // Spawn based on Shape
            switch (shape) {
                case POINT:
                    if (direction != null) builder.spawn(target, direction);
                    else builder.spawn(target);
                    break;
                case RING:
                    builder.ring(target, finalRadius, points);
                    break;
                case ORIENTED_RING:
                    if (direction != null) builder.orientedRing(target, direction, finalRadius, points);
                    else builder.ring(target, finalRadius, points);
                    break;
                case SPHERE:
                    builder.sphere(target, finalRadius, points);
                    break;
                case LINE:
                    if (endLocation != null) {
                        builder.line(target, endLocation, step);
                    } else if (direction != null) {
                        Location end = target.clone().add(direction.clone().multiply(finalRadius));
                        builder.line(target, end, step);
                    } else {
                        builder.spawn(target);
                    }
                    break;
            }
        }

        private void playDisplayEffect(Location target, Vector direction, Location endLocation, String liveSkin) {
            if (modelData == null) return;

            if (shape == Shape.LINE && endLocation != null) {
                Vector lineDir = endLocation.toVector().subtract(target.toVector());
                double distance = lineDir.length();
                if (distance < 0.01) return;
                lineDir.normalize();
                for (double d = 0; d <= distance; d += step) {
                    Location loc = target.clone().add(lineDir.clone().multiply(d));
                    ItemDisplayBuilder.create(loc)
                            .modelData(modelData[0], modelData[1], resolveSkin(liveSkin))
                            .scale(scale)
                            .transformType(transform)
                            .duration(duration)
                            .oriented(lineDir, modelBackOffset)
                            .spawn();
                }
            } else {
                ItemDisplayBuilder iBuilder = ItemDisplayBuilder.create(target)
                        .modelData(modelData[0], modelData[1], resolveSkin(liveSkin))
                        .scale(scale)
                        .transformType(transform)
                        .duration(duration);

                if (direction != null) {
                    iBuilder.oriented(direction, modelBackOffset);
                }

                iBuilder.spawn();
            }
        }
    }
}
