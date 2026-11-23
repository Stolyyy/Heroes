package me.stolyy.heroes.vfx.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@SuppressWarnings("unchecked")
public abstract class AbstractDisplayBuilder<T extends Display, B extends AbstractDisplayBuilder<T, B>> {
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
    protected int interpolationDelay = 0;
    protected int interpolationDuration = 0;
    protected Display.Brightness brightness = null;

    public AbstractDisplayBuilder(Location location) {
        this.location = location;
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

    public B rotation(float x, float y, float z, float w) {
        this.leftRotation = new Quaternionf(x, y, z, w);
        return (B) this;
    }

    public B oriented(Vector direction) {
        return oriented(direction, 0);
    }

    /**
     * Points the model's {@link #MODEL_FORWARD} axis along {@code direction} and, when
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

    public static void orient(org.bukkit.entity.Display display, Vector direction) {
        orient(display, direction, 0);
    }

    /**
     * Re-aims a live display. Recomputes the back-offset translation from the CURRENT direction
     * every call — a gravity-affected projectile's velocity bends in flight, so a translation
     * cached at spawn drifts off the aim axis within a few ticks.
     *
     * <p>Preserves rightRotation: MarauderSaberThrow writes its spin quaternion there.
     */
    public static void orient(org.bukkit.entity.Display display, Vector direction, double backOffset) {
        Transformation trans = display.getTransformation();
        Vector d = direction.clone().normalize();
        display.setTransformation(new Transformation(
                backTranslation(d, backOffset, trans.getScale()),
                calculateRotation(d),
                trans.getScale(),
                trans.getRightRotation()
        ));
    }

    /**
     * Returns a clone of the location with yaw/pitch zeroed. Display entities RENDER with
     * their own entity rotation applied UNDER the transformation's leftRotation — and since
     * Paper honors location yaw/pitch for spawned displays (PaperMC/Paper#9418, fixed by
     * #9552), a spawn or teleport location that carries look angles (eye locations,
     * projectile positions cloned from them) silently rotates the whole frame the
     * quaternion operates in. Every ability-visual teleport must pass through this so
     * leftRotation is the single orientation authority; spawns get it for free because
     * {@link #spawnLocation} applies it inside the builders' {@code spawn()}.
     */
    public static Location canonical(Location location) {
        Location l = location.clone();
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    /**
     * THE MODEL CONVENTION. Every model used by an oriented display is assumed to be authored
     * pointing along this axis in its own local space, with its up along local +Y. The resource
     * pack is the side that has to satisfy this: a model whose geometry points elsewhere is
     * rotated back onto this axis in its own {@code display} block (the {@code head} block for
     * the default transform, {@code fixed} for {@code transform: FIXED} effects), not here.
     *
     * <p>+Z is chosen because it is the axis the aim quaternion naturally produces, so the
     * correction below is the identity and the transform has nothing hidden in it. If the pack
     * ends up normalised to a different axis, changing this one line is the whole fix — the
     * correction quaternion, the back-offset translation and the length-scale axis are all
     * derived from it.
     */
    public static final Vector MODEL_FORWARD = new Vector(0, 0, 1);

    /**
     * Rotates {@link #MODEL_FORWARD} onto +Z, the axis {@link #aimRotation} maps onto the aim
     * vector. It is the inverse of the aim rotation for the forward axis itself, so the six legal
     * axis values fall straight out: +Z gives the identity, +X gives Ry(-90), -X Ry(+90),
     * -Z Ry(180), +Y Rx(+90), -Y Rx(-90).
     *
     * <p>Folded into leftRotation (L = aim * correction) rather than rightRotation because
     * rightRotation is already spoken for — MarauderSaberThrow spins the saber with it and
     * Botclaw rebuilds the rope transform with its own.
     */
    private static final Quaternionf FORWARD_CORRECTION = aimRotation(MODEL_FORWARD).invert();

    /**
     * The rotation that maps LOCAL +Z onto {@code direction} with zero roll.
     *
     * <p>A Minecraft direction with yaw t and pitch p is (-sin t cos p, -sin p, cos t cos p), and
     * Ry(-t) * Rx(p) maps +Z onto exactly that while leaving +Y in the vertical plane through the
     * aim. Note the NEGATED yaw: JOML's rotateY is right-handed (sends +Z toward +X) while
     * Minecraft yaw sends +Z toward -X, so feeding the raw yaw in mirrors the heading across X.
     */
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
     *
     * <p>Minecraft builds the matrix as translation * leftRotation * scale * rightRotation, so
     * the translation is in the entity's own unrotated frame and is NOT scaled — but the model
     * point it has to cancel is. The rear face at local -b*MODEL_FORWARD becomes -b*s*direction
     * after scale and rotation, where s is the scale component on the forward axis, so the
     * translation is that same vector negated.
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
     * {@link #MODEL_FORWARD} axis and by {@code across} on the other two. Scale is applied INSIDE
     * leftRotation, so the component that becomes "length along the aim vector" is the model's
     * own forward axis — not world Z, and not whichever axis happened to be right before the
     * convention was pinned down.
     */
    public static Vector3f forwardScale(float alongForward, float across) {
        return new Vector3f(
                MODEL_FORWARD.getX() != 0 ? alongForward : across,
                MODEL_FORWARD.getY() != 0 ? alongForward : across,
                MODEL_FORWARD.getZ() != 0 ? alongForward : across);
    }

    public B duration(long ticks) {
        this.interpolationDelay = (int) ticks;
        return (B) this;
    }

    public B interpolationDuration(int ticks) {
        this.interpolationDuration = ticks;
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

    /**
     * The location the entity is actually spawned at. Canonicalised here rather than at the
     * call sites so a future caller cannot forget it and silently reintroduce an entity-rotated
     * frame under the transformation.
     */
    protected Location spawnLocation() {
        return canonical(location);
    }

    protected void applyBase(T display) {
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
        if (interpolationDuration > 0) {
            display.setInterpolationDuration(interpolationDuration);
            display.setInterpolationDelay(0);
        }
        if (interpolationDelay > 0) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(me.stolyy.heroes.Heroes.getInstance(), display::remove, interpolationDelay);
        }
    }

    public abstract T spawn();
}