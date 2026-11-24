package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.UnaryOperator;

@SuppressWarnings("unchecked")
public abstract class AbstractDisplayBuilder<T extends Display, B extends AbstractDisplayBuilder<T, B>> {
    public static final NamespacedKey DISPLAY_TAG = DisplayTags.VFX_DISPLAY;

    protected Location location;
    protected Vector3f scale = new Vector3f(1, 1, 1);
    protected Vector3f translation = new Vector3f(0, 0, 0);
    protected Quaternionf leftRotation = new Quaternionf();
    protected Quaternionf rightRotation = new Quaternionf();
    /** Aim vector recorded by {@link #oriented}; null when the display has no intended heading. */
    protected Vector orientDirection = null;
    /** Blocks from the model pivot back to its rear face. See {@link #backTranslation}. */
    protected double modelBackOffset = 0;
    protected Billboard billboard = Billboard.FIXED;
    protected float viewRange = 1.0f;
    protected float shadowRadius = 0.0f;
    protected float shadowStrength = 1.0f;
    protected int glowColorOverride = -1; // -1 means no override
    protected boolean glowing = false;
    protected boolean persistent = false;
    protected NamespacedKey entityTag = DISPLAY_TAG;
    protected int teleportDuration = 0;
    protected int interpolationDuration = 0;
    protected long lifetimeTicks = 0;
    protected Display.Brightness brightness = null;

    public AbstractDisplayBuilder(Location location) {
        this.location = location;
    }

    /** Removes any lingering builder-spawned displays left behind by an unclean stop. */
    public static void sweepOrphans() {
        DisplayTags.sweep(DISPLAY_TAG);
    }

    public B scale(float x, float y, float z) {
        this.scale = new Vector3f(x, y, z);
        return (B) this;
    }

    public B scale(float uniform) {
        return scale(uniform, uniform, uniform);
    }

    public B translation(float x, float y, float z) {
        this.translation = new Vector3f(x, y, z);
        return (B) this;
    }

    /**
     * Points the model's {@link #modelForward()} axis along {@code direction} and, when
     * {@code backOffset} is non-zero, slides it forward so its rear face sits at the entity
     * location instead of straddling it. The translation depends on the scale, so it is
     * computed in {@link #applyBase} rather than here — builder calls can be in any order.
     */
    public B oriented(Vector direction, double backOffset) {
        this.orientDirection = direction.clone().normalize();
        this.modelBackOffset = backOffset;
        this.leftRotation = calculateRotation(this.orientDirection);
        return (B) this;
    }

    public static void orient(Display display, Vector direction) {
        orient(display, direction, 0);
    }

    /**
     * Re-aims a live display. Recomputes the back-offset translation from the CURRENT direction
     * every call — a gravity-affected projectile's velocity bends in flight, so a translation
     * cached at spawn drifts off the aim axis within a few ticks.
     *
     * <p>Preserves rightRotation: spinning displays write their roll/yaw spin quaternion there.
     */
    public static void orient(Display display, Vector direction, double backOffset) {
        Transformation trans = display.getTransformation();
        Vector d = direction.clone().normalize();
        display.setTransformation(new Transformation(
                backTranslation(d, backOffset, trans.getScale()),
                calculateRotation(d),
                trans.getScale(),
                trans.getRightRotation()
        ));
    }

    /** Re-aims a live display and sets a local-Y rightRotation spin quaternion in one transform update. */
    public static void orient(Display display, Vector direction, double backOffset, float spinRadians) {
        Transformation trans = display.getTransformation();
        Vector d = direction.clone().normalize();
        display.setTransformation(new Transformation(
                backTranslation(d, backOffset, trans.getScale()),
                calculateRotation(d),
                trans.getScale(),
                new Quaternionf().rotateY(spinRadians)
        ));
    }

    /** Teleports {@code display} to a canonicalized location and, when heading is non-zero, re-orients it. */
    public static void follow(Display display, Location location, Vector direction, double backOffset) {
        display.teleport(canonical(location));
        if (direction != null && direction.lengthSquared() > 1.0E-12) {
            orient(display, direction, backOffset);
        }
    }

    /** Teleports {@code display} to a canonicalized location, re-orients along heading, and applies Y-spin. */
    public static void follow(Display display, Location location, Vector direction, double backOffset, float spinRadians) {
        display.teleport(canonical(location));
        if (direction != null && direction.lengthSquared() > 1.0E-12) {
            orient(display, direction, backOffset, spinRadians);
        }
    }

    /** Updates {@code display}'s rightRotation to spin around local Y by {@code radians}. */
    public static void spin(Display display, float radians) {
        edit(display, t -> new Transformation(
                t.getTranslation(), t.getLeftRotation(), t.getScale(), new Quaternionf().rotateY(radians)));
    }

    /** Stretches an oriented display from {@code from} to {@code to} along the model's forward axis. */
    public static void span(Display display, Location from, Location to,
                            double modelLength, float thickness, boolean centerOrigin) {
        Vector span = to.toVector().subtract(from.toVector());
        double length = span.length();
        if (length < 1.0E-6) return;

        Vector spanDir = span.clone().normalize();
        display.teleport(canonical(from));

        Vector3f translation = centerOrigin
                ? new Vector3f((float) (spanDir.getX() * length / 2.0),
                               (float) (spanDir.getY() * length / 2.0),
                               (float) (spanDir.getZ() * length / 2.0))
                : new Vector3f();

        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(
                translation,
                calculateRotation(spanDir),
                forwardScale((float) (length / modelLength), thickness),
                new Quaternionf()));
    }

    /** Applies a transformation update immediately in one call. */
    public static void edit(Display display, UnaryOperator<Transformation> change) {
        display.setTransformation(change.apply(display.getTransformation()));
    }

    /** Interpolates a display to a new transformation over {@code ticks} client interpolation ticks. */
    public static void transformOver(Display display, int ticks, UnaryOperator<Transformation> change) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        edit(display, change);
    }

    /** Returns a copy of {@code t} with only its translation replaced. */
    public static Transformation withTranslation(Transformation t, Vector3f translation) {
        return new Transformation(translation, t.getLeftRotation(), t.getScale(), t.getRightRotation());
    }

    /**
     * Converts a Minecraft yaw in degrees to a JOML Y-axis quaternion. Minecraft yaw is clockwise
     * from +Z while JOML's {@code rotateY} is counter-clockwise, so the angle is negated.
     */
    public static Quaternionf yawRotation(float yawDegrees) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDegrees));
    }

    /**
     * Returns a clone of the location with yaw/pitch zeroed. Display entities RENDER with
     * their own entity rotation applied UNDER the transformation's leftRotation — and since
     * Paper honors location yaw/pitch for spawned displays, a spawn or teleport location that
     * carries look angles (eye locations, projectile positions cloned from them) silently
     * rotates the whole frame the quaternion operates in. Every ability-visual teleport must
     * pass through this so leftRotation is the single orientation authority; spawns get it
     * for free because {@link #spawnLocation} applies it inside the builders' {@code spawn()}.
     */
    public static Location canonical(Location location) {
        Location l = location.clone();
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    // THE MODEL CONVENTION. Every model used by an oriented display is assumed to be authored
    // pointing along this axis in its own local space, with its up along local +Y. The resource
    // pack is the side that has to satisfy this: a model whose geometry points elsewhere is
    // rotated back onto this axis in its own display block, not here.
    // +Z is chosen because it is the axis the aim quaternion naturally produces, so the
    // correction below is the identity and the transform has nothing hidden in it. If the pack
    // ends up normalised to a different axis, changing this one vector is the whole fix.
    private static final Vector MODEL_FORWARD = new Vector(0, 0, 1);

    /** Returns a fresh copy of the plugin's canonical model forward axis. */
    public static Vector modelForward() {
        return MODEL_FORWARD.clone();
    }

    // Rotates MODEL_FORWARD onto +Z, the axis aimRotation maps onto the aim vector.
    private static final Quaternionf FORWARD_CORRECTION = aimRotation(MODEL_FORWARD).invert();

    private static Quaternionf aimRotation(Vector direction) {
        Vector d = direction.clone().normalize();
        float yaw = (float) Math.atan2(-d.getX(), d.getZ());
        float pitch = (float) Math.asin(-d.getY());
        return new Quaternionf().rotateY(-yaw).rotateX(pitch);
    }

    /** The rotation that points {@link #MODEL_FORWARD} along {@code direction} with zero roll. */
    public static Quaternionf calculateRotation(Vector direction) {
        return aimRotation(direction).mul(FORWARD_CORRECTION);
    }

    /**
     * Translation that moves a model's rear face onto the entity location, given how far the
     * rear face sits behind the pivot along the forward axis.
     */
    public static Vector3f backTranslation(Vector direction, double backOffset, Vector3f scale) {
        if (backOffset == 0) return new Vector3f();
        Vector d = direction.clone().normalize();
        float k = (float) (backOffset * forwardScale(scale));
        return new Vector3f((float) d.getX() * k, (float) d.getY() * k, (float) d.getZ() * k);
    }

    /** The component of {@code scale} that stretches the model along {@link #MODEL_FORWARD}. */
    public static float forwardScale(Vector3f scale) {
        return (float) Math.abs(MODEL_FORWARD.getX() * scale.x()
                + MODEL_FORWARD.getY() * scale.y()
                + MODEL_FORWARD.getZ() * scale.z());
    }

    /**
     * Builds a scale vector that stretches the model by {@code alongForward} on its
     * {@link #MODEL_FORWARD} axis and by {@code across} on the other two.
     */
    public static Vector3f forwardScale(float alongForward, float across) {
        return new Vector3f(
                MODEL_FORWARD.getX() != 0 ? alongForward : across,
                MODEL_FORWARD.getY() != 0 ? alongForward : across,
                MODEL_FORWARD.getZ() != 0 ? alongForward : across);
    }

    /** Schedules automatic entity removal after {@code ticks} ticks (0 or less means persistent for this session). */
    public B lifetime(long ticks) {
        this.lifetimeTicks = ticks;
        return (B) this;
    }

    public B persistent(boolean persistent) {
        this.persistent = persistent;
        return (B) this;
    }

    /** Overrides the PDC tag stamped on spawn (replaces the default {@link #DISPLAY_TAG}). */
    public B tag(NamespacedKey key) {
        this.entityTag = key;
        return (B) this;
    }

    public B teleportDuration(int ticks) {
        this.teleportDuration = ticks;
        return (B) this;
    }

    public B interpolationDuration(int ticks) {
        this.interpolationDuration = ticks;
        return (B) this;
    }

    public B smooth(int teleportTicks, int interpolationTicks) {
        this.teleportDuration = teleportTicks;
        this.interpolationDuration = interpolationTicks;
        return (B) this;
    }

    public B billboard(Billboard billboard) {
        this.billboard = billboard;
        return (B) this;
    }

    public B viewRange(float range) {
        this.viewRange = range;
        return (B) this;
    }

    public B shadow(float radius, float strength) {
        this.shadowRadius = radius;
        this.shadowStrength = strength;
        return (B) this;
    }

    public B glow(boolean glowing, Color color) {
        this.glowing = glowing;
        if (color != null) {
            this.glowColorOverride = color.asRGB();
        }
        return (B) this;
    }

    /**
     * Overrides the block/sky light level the display renders at, independent of its position.
     * Use (15, 15) to force full brightness (useful for entities buried in blocks to avoid shadow darkness).
     * Values are clamped to 0–15. Pass negative values to clear the override.
     */
    public B brightness(int blockLight, int skyLight) {
        if (blockLight < 0 || skyLight < 0) {
            this.brightness = null;
        } else {
            this.brightness = new Display.Brightness(
                    Math.min(15, blockLight),
                    Math.min(15, skyLight)
            );
        }
        return (B) this;
    }

    protected World requireWorld() {
        if (location == null || location.getWorld() == null) {
            throw new IllegalStateException("Cannot spawn display with null world at " + location);
        }
        return location.getWorld();
    }

    /**
     * The location the entity is actually spawned at. Canonicalised here rather than at the
     * call sites so a future caller cannot forget it and silently reintroduce an entity-rotated
     * frame under the transformation.
     */
    protected Location spawnLocation() {
        return canonical(location);
    }

    protected void applyBase(T display) {
        display.setPersistent(persistent);
        if (entityTag != null) {
            DisplayTags.tag(display, entityTag);
        }
        Vector3f finalTranslation = orientDirection == null
                ? translation
                : new Vector3f(translation).add(backTranslation(orientDirection, modelBackOffset, scale));
        Transformation transform = new Transformation(finalTranslation, leftRotation, scale, rightRotation);
        display.setTransformation(transform);
        display.setBillboard(billboard);
        display.setViewRange(viewRange);
        display.setShadowRadius(shadowRadius);
        display.setShadowStrength(shadowStrength);
        display.setGlowing(glowing);
        if (glowColorOverride != -1) {
            display.setGlowColorOverride(Color.fromRGB(glowColorOverride));
        }
        if (brightness != null) {
            display.setBrightness(brightness);
        }
        if (teleportDuration > 0) {
            display.setTeleportDuration(teleportDuration);
        }
        if (interpolationDuration > 0) {
            display.setInterpolationDuration(interpolationDuration);
            display.setInterpolationDelay(0);
        }
        if (lifetimeTicks > 0) {
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), display::remove, lifetimeTicks);
        }
    }

    public abstract T spawn();
}
