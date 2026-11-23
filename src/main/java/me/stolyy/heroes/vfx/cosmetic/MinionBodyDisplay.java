package me.stolyy.heroes.vfx.cosmetic;

import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Full-body minion visual: an ITEM_DISPLAY riding the invisible NPC as a passenger, rendering the
 * minion's model at body scale. Replaces the worn-helmet trick for minions whose skin config opts
 * in with a {@code body:} block (see {@link CosmeticManager#resolveMinionBody}).
 *
 * <p>Why a display entity instead of the helmet: the helmet renders on the HEAD bone, so it is
 * capped at display scale 4, pitches with the head (a full-body model nods its whole body at
 * whatever the AI looks at), and needs per-model {@code display.head} translation gymnastics to
 * reach the ground. The display entity is its own anchor with an unbounded scale, and it syncs to
 * BODY yaw only — the model turns with movement and never pitches.
 *
 * <p>{@link ItemDisplay.ItemDisplayTransform#NONE} is load-bearing: it renders the RAW model
 * geometry (16 units = 1 block, model center (8,8,8) at the display origin) and ignores the
 * pack's {@code display.head} block entirely, so the pack needs no changes for this path — with
 * one exception that bit us. The head block's baked {@code [0,180,0]} yaw was NOT inert: it was
 * cancelling the {@code Axis.YP.rotation(PI)} that vanilla's ItemDisplay renderer applies to every
 * item display. Dropping the head block means {@link #applyTransformation} must carry that 180.
 *
 * <p>Passenger mechanics: position sync is free, but vanilla teleports EJECT passengers, so
 * {@link #tick()} re-mounts when needed. The display spawns with a zeroed location yaw
 * ({@code canonical}) — the entity's own rotation would compound with the transformation's
 * leftRotation, which is the sole owner of facing here.
 */
public class MinionBodyDisplay {

    private final LivingEntity body;
    private final ItemDisplay display;
    private final float scale;
    private final float anchorY;
    private final float modelYaw;
    private float lastYaw = Float.NaN;

    /**
     * @param body   the minion's NPC entity (the vehicle)
     * @param config the resolved {@code body:} section — model keys plus scale/anchor_y/model_yaw
     * @param heroName fallback for the model's hero half when the section omits {@code hero}
     */
    public MinionBodyDisplay(LivingEntity body, ConfigurationSection config, String heroName) {
        this.body = body;
        this.scale = (float) Configs.requireDouble(config, "scale");
        this.anchorY = (float) Configs.requireDouble(config, "anchor_y");
        this.modelYaw = (float) Configs.requireDouble(config, "model_yaw");

        ItemStack item = CosmeticManager.minionBodyItem(config, heroName);

        Location loc = body.getLocation().clone();
        loc.setYaw(0);
        loc.setPitch(0);
        this.display = body.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
            // Smooth turning: each transformation update interpolates over 2 ticks client-side.
            d.setInterpolationDuration(2);
            d.setInterpolationDelay(0);
        });
        applyTransformation(body.getBodyYaw());
        body.addPassenger(display);
    }

    /** Per-tick from {@code MinionHero.tick()}: keep mounted (teleports eject) and track body yaw. */
    public void tick() {
        if (display.getVehicle() != body) body.addPassenger(display);

        float yaw = body.getBodyYaw();
        if (!Float.isNaN(lastYaw) && Math.abs(yaw - lastYaw) < 0.5f) return;
        applyTransformation(yaw);
    }

    private void applyTransformation(float bodyYaw) {
        lastYaw = bodyYaw;
        // rotateY(-yaw): MC yaw is clockwise from +Z while JOML's +Y rotation is counter-clockwise,
        // so negating maps the model's authored forward onto the entity's facing (the same
        // convention AbstractDisplayBuilder uses). model_yaw is the per-model correction on top.
        //
        // The +180 is VANILLA's, not the model's: the ItemDisplay sub-renderer unconditionally does
        // poseStack.mulPose(Axis.YP.rotation(PI)) on the item INSIDE the transformation (1.21.11
        // client, DisplayRenderer$ItemDisplayRenderer#renderInner), so a model authored along +Z
        // renders along -Z whatever leftRotation says. Ability visuals never see it because they
        // render with transform HEAD and every model in this pack carries a display.head rotation
        // of [0,180,0] that cancels it; NONE drops that cancellation, which is why the minion
        // bodies ran exactly backwards at model_yaw 0.
        Quaternionf facing = new Quaternionf()
                .rotateY((float) Math.toRadians(-(bodyYaw + modelYaw + 180)));
        // Translation is applied outside the rotation — a pure world-frame vertical lift that
        // brings the model's feet from the ride position down/up to the ground.
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(
                new Vector3f(0, anchorY, 0),
                facing,
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
    }

    public void remove() {
        display.remove();
    }

    /** The live display entity — the minion's VISIBLE body, e.g. for teammate glow targeting. */
    public ItemDisplay getDisplay() {
        return display;
    }
}
