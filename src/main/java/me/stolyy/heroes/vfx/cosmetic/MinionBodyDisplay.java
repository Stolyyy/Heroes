package me.stolyy.heroes.vfx.cosmetic;

import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.ItemDisplayBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Full-body minion visual: an ITEM_DISPLAY riding the invisible NPC as a passenger, rendering the
 * minion's model at body scale.
 *
 * <p>{@link ItemDisplay.ItemDisplayTransform#NONE} renders raw model geometry without head-block
 * baking, so {@link #applyTransformation} adds {@value #VANILLA_NONE_YAW_OFFSET} to counter the
 * 180-degree rotation vanilla's ItemDisplay renderer applies unconditionally inside the transform.
 */
public class MinionBodyDisplay {
    private static final int TURN_INTERPOLATION_TICKS = 2;
    private static final float YAW_EPSILON_DEG = 0.5f;
    private static final float VANILLA_NONE_YAW_OFFSET = 180f;

    private final LivingEntity body;
    private final ItemDisplay display;
    private final float scale;
    private final float anchorY;
    private final float modelYaw;
    private float lastYaw = Float.NaN;

    /**
     * @param body     the minion's NPC entity (the vehicle)
     * @param config   the resolved {@code body:} section — model keys plus scale/anchor_y/model_yaw
     * @param heroName fallback for the model's hero half when the section omits {@code hero}
     */
    public MinionBodyDisplay(LivingEntity body, ConfigurationSection config, String heroName) {
        this.body = body;
        this.scale = (float) Configs.requireDouble(config, "scale");
        this.anchorY = (float) Configs.requireDouble(config, "anchor_y");
        this.modelYaw = (float) Configs.requireDouble(config, "model_yaw");

        ItemStack item = CosmeticManager.minionBodyItem(config, heroName);
        this.display = ItemDisplayBuilder.create(body.getLocation())
                .item(item)
                .transformType(ItemDisplay.ItemDisplayTransform.NONE)
                .interpolationDuration(TURN_INTERPOLATION_TICKS)
                .spawn();

        applyTransformation(body.getBodyYaw());
        body.addPassenger(display);
    }

    /** Per-tick from {@code MinionHero.tick()}: keep mounted (teleports eject) and track body yaw. */
    public void tick() {
        if (!body.isValid() || body.isDead() || !display.isValid()) return;
        if (display.getVehicle() != body) body.addPassenger(display);

        float yaw = body.getBodyYaw();
        if (!Float.isNaN(lastYaw) && Math.abs(yaw - lastYaw) < YAW_EPSILON_DEG) return;
        applyTransformation(yaw);
    }

    private void applyTransformation(float bodyYaw) {
        lastYaw = bodyYaw;
        Quaternionf facing = AbstractDisplayBuilder.yawRotation(bodyYaw + modelYaw + VANILLA_NONE_YAW_OFFSET);
        AbstractDisplayBuilder.transformOver(display, TURN_INTERPOLATION_TICKS, t -> new Transformation(
                new Vector3f(0, anchorY, 0),
                facing,
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
    }

    public void remove() {
        display.remove();
    }

    /** The live display entity — the minion's visible body, e.g. for teammate glow targeting. */
    public ItemDisplay getDisplay() {
        return display;
    }
}
