package me.stolyy.heroes.physics;
import me.stolyy.heroes.control.Controller;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Block collision, line-of-sight, swept movement clamping, and unstick utilities.
 */
public class WallDetection {
    public static final double DEFAULT_ESCAPE_MAX_DISTANCE = 3.0;
    public static final double DEFAULT_ESCAPE_STEP = 0.1;
    private static final double STEP_SIZE = 0.0625;
    private static final double EPSILON = 1.0E-7;
    // Trimmed off an entity hitbox before testing it against block geometry: resting on a face and
    // sub-millimetre sinking into the floor are not "inside a block".
    private static final double HITBOX_MARGIN = 0.02;
    // Horizontal first, then up, then down — see escapeOffset(World, BoundingBox, double, double).
    private static final Vector[] ESCAPE_DIRECTIONS = {
            new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(0, 0, 1), new Vector(0, 0, -1),
            new Vector(0, 1, 0), new Vector(0, -1, 0)
    };

    public static boolean detectWall(Location location, double radius){
        if(!isValidLocation(location) || !isFinite(radius)) {
            return false; //Input is invalid
        }

        BoundingBox box = BoundingBox.of(location, radius, radius, radius);
        World world = location.getWorld();

        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block currentBlock = world.getBlockAt(x, y, z);
                    if(currentBlock.isSolid() && !currentBlock.isPassable()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Finds the solid, non-passable block whose AABB overlaps the sphere (center, radius) and
     * lies nearest the center, returning that block's center location, or null if the sphere
     * contains no solid block. Spheres have no direction, so this is how their hitboxes detect
     * walls: scan the enclosed blocks rather than ray-tracing along an axis.
     */
    public static Location nearestSolidBlockInSphere(Location center, double radius){
        World world = center.getWorld();
        double cx = center.getX(), cy = center.getY(), cz = center.getZ();
        double r2 = radius * radius;

        Location best = null;
        double bestDist2 = Double.MAX_VALUE;

        for (int x = (int) Math.floor(cx - radius); x <= (int) Math.floor(cx + radius); x++) {
            for (int y = (int) Math.floor(cy - radius); y <= (int) Math.floor(cy + radius); y++) {
                for (int z = (int) Math.floor(cz - radius); z <= (int) Math.floor(cz + radius); z++) {
                    // Closest point on the block's [x..x+1] AABB to the sphere center.
                    double clampedX = Math.max(x, Math.min(cx, x + 1.0));
                    double clampedY = Math.max(y, Math.min(cy, y + 1.0));
                    double clampedZ = Math.max(z, Math.min(cz, z + 1.0));
                    double dx = clampedX - cx, dy = clampedY - cy, dz = clampedZ - cz;
                    double dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 > r2 || dist2 >= bestDist2) continue;

                    Block block = world.getBlockAt(x, y, z);
                    if (block.isSolid() && !block.isPassable()) {
                        bestDist2 = dist2;
                        best = new Location(world, x + 0.5, y + 0.5, z + 0.5);
                    }
                }
            }
        }
        return best;
    }

    /** True if {@code location} sits inside a solid, non-passable block. */
    public static boolean isInsideSolid(Location location){
        Block block = location.getBlock();
        return block.isSolid() && !block.isPassable();
    }

    /**
     * Walks {@code origin} backwards along {@code back} in {@code stepSize} increments and returns the
     * first position clear of solid geometry, giving up after {@code maxDistance} and returning
     * {@code origin} unchanged. Used to lift an explosion center out of the block a projectile buried
     * it in: a center inside the floor loses most of its blast volume and skews radial knockback
     * downward. Retreating along the incoming path (rather than to the nearest open space in any
     * direction) keeps the blast on the trajectory the shot actually travelled.
     *
     * @param back direction to retreat in, i.e. the reverse of the incoming trajectory. Must be non-zero.
     */
    public static Location retreatFromSolid(Location origin, Vector back, double maxDistance, double stepSize){
        if (!isInsideSolid(origin)) return origin;

        Vector step = back.clone().normalize().multiply(stepSize);
        Location probe = origin.clone();
        for (double walked = stepSize; walked <= maxDistance; walked += stepSize) {
            probe.add(step);
            if (!isInsideSolid(probe)) return probe;
        }
        return origin;
    }

    public static boolean rayCast(Location start, Location end){
        if(!isValidLocation(start) || !isValidLocation(end)) {
            return false;
        }

        Vector v = end.toVector().subtract(start.toVector());
        double length = v.length();

        if(length <= STEP_SIZE) {
            return start.getBlock().isSolid() && !start.getBlock().isPassable();
        }

        v.normalize();
        Location current = start.clone();
        double distanceTraveled = 0;
        while (distanceTraveled < length) {
            current.add(v.clone().multiply(STEP_SIZE));
            distanceTraveled += STEP_SIZE;

            Block block = current.getBlock();
            if (block.isSolid() && !block.isPassable()) {
                return true;
            }
        }
        return end.getBlock().isSolid() && !end.getBlock().isPassable();
    }

    /**
     * Clamps each component of velocity so the bounding box won't move into a solid block.
     * For each axis, finds the nearest wall in the direction of movement and limits the
     * velocity to the available gap.
     *
     * @param world       the world the bounding box is in
     * @param boundingBox the entity's current occupied space
     * @param velocity    the desired velocity
     * @return the clamped velocity
     */
    public static Vector clampToWalls(World world, BoundingBox boundingBox, Vector velocity) {
        if (world == null || velocity == null || boundingBox == null) return velocity;

        Vector result = velocity.clone();

        // X axis
        double vx = result.getX();
        if (Math.abs(vx) >= 0.001) {
            double leadX = vx > 0 ? boundingBox.getMaxX() : boundingBox.getMinX();
            double sign = Math.signum(vx);
            double availableX = findAvailableDistance(world, leadX, sign, 0,
                    boundingBox.getMinY(), boundingBox.getMaxY(),
                    boundingBox.getMinZ(), boundingBox.getMaxZ(),
                    Math.abs(vx));
            if (Math.abs(vx) > availableX) {
                result.setX(sign * availableX);
            }
        }

        // Y axis
        double vy = result.getY();
        if (Math.abs(vy) >= 0.001) {
            double leadY = vy > 0 ? boundingBox.getMaxY() : boundingBox.getMinY();
            double sign = Math.signum(vy);
            double availableY = findAvailableDistance(world, leadY, sign, 1,
                    boundingBox.getMinX(), boundingBox.getMaxX(),
                    boundingBox.getMinZ(), boundingBox.getMaxZ(),
                    Math.abs(vy));
            if (Math.abs(vy) > availableY) {
                result.setY(sign * availableY);
            }
        }

        // Z axis
        double vz = result.getZ();
        if (Math.abs(vz) >= 0.001) {
            double leadZ = vz > 0 ? boundingBox.getMaxZ() : boundingBox.getMinZ();
            double sign = Math.signum(vz);
            double availableZ = findAvailableDistance(world, leadZ, sign, 2,
                    boundingBox.getMinX(), boundingBox.getMaxX(),
                    boundingBox.getMinY(), boundingBox.getMaxY(),
                    Math.abs(vz));
            if (Math.abs(vz) > availableZ) {
                result.setZ(sign * availableZ);
            }
        }

        // Per-axis clamping tests each axis against the UNMOVED box, so a diagonal move can slip
        // past a lone corner that neither axis test saw. Back the accepted move off along itself
        // until the destination is actually clear. Skipped when the box is already embedded —
        // otherwise this check would zero every move and rebuild the cage removed above.
        if (overlapsCollision(world, boundingBox)) return result;
        for (double scale = 1.0; scale > 0.0; scale -= 0.25) {
            Vector candidate = result.clone().multiply(scale);
            if (!overlapsCollision(world, boundingBox.clone().shift(candidate))) return candidate;
        }
        return new Vector(0, 0, 0);
    }

    private static double findAvailableDistance(World world,
                                                double leadFace, double sign, int axis,
                                                double min1, double max1,
                                                double min2, double max2,
                                                double requested) {
        int startBlock = (int) Math.floor(leadFace + sign * 0.001);
        int endBlock   = (int) Math.floor(leadFace + sign * requested);

        int lo1 = (int) Math.floor(min1);
        int hi1 = (int) Math.floor(max1 - 0.001);
        int lo2 = (int) Math.floor(min2);
        int hi2 = (int) Math.floor(max2 - 0.001);

        int from = sign > 0 ? startBlock : endBlock;
        int to   = sign > 0 ? endBlock   : startBlock;

        // The two axes perpendicular to the movement axis, matching the order clampToWalls passes
        // min1/max1 and min2/max2 in (and how c1/c2 map to block coordinates below).
        int perp1 = axis == 0 ? 1 : 0;
        int perp2 = axis == 2 ? 1 : 2;

        double available = requested;

        for (int coord = from; coord <= to; coord++) {
            for (int c1 = lo1; c1 <= hi1; c1++) {
                for (int c2 = lo2; c2 <= hi2; c2++) {
                    int bx, by, bz;
                    if (axis == 0) { bx = coord; by = c1; bz = c2; }
                    else if (axis == 1) { bx = c1; by = coord; bz = c2; }
                    else { bx = c1; by = c2; bz = coord; }

                    Block block = world.getBlockAt(bx, by, bz);
                    if (block.isPassable()) continue;

                    // Real collision geometry, not the block's full cube. isSolid() is true for a
                    // bottom slab, so the old "face is at coord / coord + 1" model invented walls
                    // where there is only air — and refused moves into that air.
                    // getCollisionShape() is block-local, so parts are shifted into world space.
                    for (BoundingBox part : block.getCollisionShape().getBoundingBoxes()) {
                        part.shift(bx, by, bz);

                        // Only parts that straddle the box on BOTH perpendicular axes are in the
                        // way; the comparisons are strict, so a part the box merely rests against
                        // never blocks it (this is what stopped a car grazing a wall dead).
                        if (axisMax(part, perp1) <= min1 || axisMin(part, perp1) >= max1) continue;
                        if (axisMax(part, perp2) <= min2 || axisMin(part, perp2) >= max2) continue;

                        double partFace = sign > 0 ? axisMin(part, axis) : axisMax(part, axis);
                        double gap = (partFace - leadFace) * sign;

                        if (gap < 0) {
                            // Already overlapping this part. Returning 0 here (the old behaviour,
                            // whose comment claimed the opposite) is what turned one frame of clip
                            // into a permanent cage: an embedded box overlaps on all three axes, so
                            // every axis clamped to zero and the mover could never drive out.
                            // Allow the move when it heads OUT of the part — the leading face is
                            // already past the part's centre on this axis — and refuse it when it
                            // would only bury the box deeper.
                            double partCenter = (axisMin(part, axis) + axisMax(part, axis)) * 0.5;
                            if ((leadFace - partCenter) * sign > 0) continue;
                            return 0;
                        }

                        if (gap < available) available = gap;
                    }
                }
            }
        }
        return available;
    }

    public static boolean isAboveVoid(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        int minY = world.getMinHeight();
        for (int y = loc.getBlockY() - 1; y >= minY; y--) {
            if (world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if {@code box} overlaps the real collision geometry of any block it spans.
     * <p>
     * Unlike everything above, this does NOT model a solid block as a full 1x1x1 cube. That
     * distinction is the whole point: {@code Block#isSolid()} is true for a bottom slab (its
     * collision bounds average 0.833, over the 0.729 threshold vanilla uses for "solid"), so cube
     * logic cannot tell "standing on a slab" from "head buried in a slab".
     * <p>
     * {@link Block#getCollisionShape()} returns the shape in BLOCK-LOCAL coordinates (0..1) — the
     * CraftBlock implementation passes the NMS shape through without offsetting it by the block
     * position — so the query box is shifted into each block's frame, not the reverse.
     */
    public static boolean overlapsCollision(World world, BoundingBox box) {
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX() - EPSILON); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY() - EPSILON); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ() - EPSILON); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isPassable()) continue;
                    if (block.getCollisionShape().overlaps(box.clone().shift(-x, -y, -z))) return true;
                }
            }
        }
        return false;
    }

    /**
     * True if {@code entity}'s own hitbox is embedded in block collision geometry — i.e. something
     * put it somewhere it could never have walked to.
     * <p>
     * This is the check the SUFFOCATION damage event cannot do for us: vanilla's {@code isInWall}
     * tests a 1e-6-thin slice at the EYE position and requires {@code isSuffocating}, which is
     * {@code blocksMotion() && isCollisionShapeFullBlock()}. A slab, stair or any other partial
     * block traps a player completely silently.
     */
    public static boolean isTrapped(Entity entity) {
        return overlapsCollision(entity.getWorld(), trappedBox(entity));
    }

    /**
     * Shortest axis-aligned offset that lifts {@code box} clear of all block collision geometry, or
     * null when nothing within {@code maxDistance} frees it. Distance is the outer loop, so the
     * result really is the nearest way out; at equal distance horizontal wins, because a body
     * embedded in a wall wants to pop out sideways rather than be shoved up through a ceiling.
     */
    public static Vector escapeOffset(World world, BoundingBox box, double maxDistance, double stepSize) {
        for (double distance = stepSize; distance <= maxDistance; distance += stepSize) {
            for (Vector direction : ESCAPE_DIRECTIONS) {
                Vector offset = direction.clone().multiply(distance);
                if (!overlapsCollision(world, box.clone().shift(offset))) return offset;
            }
        }
        return null;
    }

    /** {@link #escapeOffset(World, BoundingBox, double, double)} for an entity's own hitbox. */
    public static Vector escapeOffset(Entity entity, double maxDistance, double stepSize) {
        return escapeOffset(entity.getWorld(), trappedBox(entity), maxDistance, stepSize);
    }

    public static RayTraceResult rayTraceBlock(Location start, Vector direction, double maxDistance) {
        return start.getWorld().rayTraceBlocks(start, direction, maxDistance, FluidCollisionMode.NEVER, true);
    }

    public static void unstick(Controller controller) {
        if (controller == null) return;
        LivingEntity entity = controller.getEntity();
        if (entity == null || !isTrapped(entity)) return;
        Vector out = escapeOffset(entity, DEFAULT_ESCAPE_MAX_DISTANCE, DEFAULT_ESCAPE_STEP);
        if (out != null) controller.teleport(controller.getLocation().add(out));
    }

    public static void safeTeleport(Controller controller, Location target) {
        controller.teleport(target);
        unstick(controller);
    }

    /** Ground probe resilient to slab/ledge overhanging corners where {@code isOnGround()} flickers false. */
    public static boolean isGrounded(LivingEntity entity) {
        if (entity.isOnGround()) return true;
        if (entity.getVelocity().getY() > 0.1) return false;
        BoundingBox box = entity.getBoundingBox();
        World world = entity.getWorld();
        int y = (int) Math.floor(box.getMinY() - 0.06);
        if (box.getMinY() - y > 1.06) return false;
        double inset = 0.02;
        double[][] corners = {
                {box.getMinX() + inset, box.getMinZ() + inset},
                {box.getMaxX() - inset, box.getMinZ() + inset},
                {box.getMinX() + inset, box.getMaxZ() - inset},
                {box.getMaxX() - inset, box.getMaxZ() - inset}};
        for (double[] corner : corners) {
            if (world.getBlockAt((int) Math.floor(corner[0]), y, (int) Math.floor(corner[1]))
                    .getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdjacentToSolidWall(Location loc) {
        Block block = loc.getBlock();
        return block.getRelative(BlockFace.NORTH).getType().isSolid()
                || block.getRelative(BlockFace.SOUTH).getType().isSolid()
                || block.getRelative(BlockFace.EAST).getType().isSolid()
                || block.getRelative(BlockFace.WEST).getType().isSolid();
    }

    private static BoundingBox trappedBox(Entity entity) {
        return entity.getBoundingBox().expand(-HITBOX_MARGIN);
    }

    private static double axisMin(BoundingBox box, int axis) {
        return axis == 0 ? box.getMinX() : axis == 1 ? box.getMinY() : box.getMinZ();
    }

    private static double axisMax(BoundingBox box, int axis) {
        return axis == 0 ? box.getMaxX() : axis == 1 ? box.getMaxY() : box.getMaxZ();
    }

    private static boolean isValidLocation(Location location) {
        return location != null && location.getWorld() != null &&
                isFinite(location.getX()) && isFinite(location.getY()) && isFinite(location.getZ());
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}