package me.stolyy.heroes.physics;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Hitbox {

    // Distance to back a directional wall hit off the struck face, so the reported location lands
    // in the air the ray travelled through rather than exactly on the block boundary (where it
    // would floor into the solid block on ~half of hits and break callers that use it as a center).
    private static final double WALL_HIT_BACKOFF = 0.05;

    // --- Static target validation ---

    /**
     * Basic target validity check. Game-independent.
     */
    public static boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity.isDead()) return false;

        // NOTE: minion invincibility is intentionally NOT checked here. isValidTarget gates both
        // attacker and victim, so checking it here disabled invincible minions' own attacks.
        // Invincibility is now enforced victim-only in Game.canTarget.

        if (entity instanceof Player player) {
            if (player.getGameMode() != org.bukkit.GameMode.ADVENTURE) return false;
        }
        return true;
    }

    /**
     * Checks if entity is alive in any game. Static convenience.
     */
    public static boolean isAlive(LivingEntity entity) {
        if (!isValidTarget(entity)) return false;
        // Practice dummies belong to no Game, so the lookup below rejected them and every
        // hitbox-based attack silently whiffed on the practice range. GameManager.canTarget
        // already has an explicit practice bypass, so a dummy would pass the team filter and
        // then get dropped here — the two gates disagreed.
        if (me.stolyy.heroes.game.PracticeManager.isDummy(entity)) return true;
        Game game = GameManager.getEntityGame(entity);
        if (game == null) return false;
        return game.isAlive(entity);
    }

    // --- Instance fields ---

    private Location origin;
    private final Shape shape;
    private final List<Predicate<LivingEntity>> filters = new ArrayList<>();
    
    private boolean checkWalls = false;
    private boolean pierce = true;
    private Location wallHitLocation = null;
    
    private Consumer<LivingEntity> onHit;
    private BiConsumer<LivingEntity, Location> onHitWithLocation;
    private Consumer<Location> onHitWall;
    private Consumer<Location> onExpire;

    private boolean trackingHits = false;
    private long hitDelay = -1;  // -1 = never re-hit (default), >= 0 = ms before re-hit
    private final Map<UUID, Long> hitHistory = new HashMap<>();

    private Hitbox(Location origin, Shape shape) {
        this.origin = origin;
        this.shape = shape;
    }

    public static Hitbox sphere(Location center, double radius) {
        return new Hitbox(center, new Sphere(radius));
    }

    public static Hitbox cylinder(Location center, Vector axis, double radius, double height) {
        return new Hitbox(center, new Cylinder(axis, radius, height));
    }

    public static Hitbox cone(Location center, Vector direction, double radius, double height) {
        return new Hitbox(center, new Cone(direction, 0, radius, height));
    }

    /**
     * Cone SEGMENT (frustum): starts {@code minRadius} wide at the origin instead of tapering to
     * a point — the point-apex cone makes close-range hits feel like threading a needle.
     */
    public static Hitbox cone(Location center, Vector direction, double minRadius, double radius, double height) {
        return new Hitbox(center, new Cone(direction, minRadius, radius, height));
    }

    /**
     * Rectangular prism swept along {@code direction}: {@code width}/{@code height} are the FULL
     * cross-section side lengths (a 1x1 profile = width 1, height 1), {@code length} the reach.
     */
    public static Hitbox box(Location start, Vector direction, double width, double height, double length) {
        return new Hitbox(start, new Box(direction, width, height, length));
    }

    // --- Fluent API ---

    public Hitbox filter(Predicate<LivingEntity> predicate) {
        filters.add(predicate);
        return this;
    }

    public Hitbox exclude(Entity entity) {
        return filter(e -> !e.getUniqueId().equals(entity.getUniqueId()));
    }

    public Hitbox excludeTeammates(LivingEntity caster) {
        return filter(e -> GameManager.canTarget(caster, e));
    }

    public Hitbox checkWalls(boolean check) {
        this.checkWalls = check;
        return this;
    }

    public Hitbox pierce(boolean pierce) {
        this.pierce = pierce;
        return this;
    }

    public Hitbox onHit(Consumer<LivingEntity> callback) {
        this.onHit = callback;
        return this;
    }

    public Hitbox onHit(BiConsumer<LivingEntity, Location> callback) {
        this.onHitWithLocation = callback;
        return this;
    }

    public Hitbox onHitWall(Consumer<Location> callback) {
        this.onHitWall = callback;
        return this;
    }

    public Hitbox onExpire(Consumer<Location> callback) {
        this.onExpire = callback;
        return this;
    }

    public Hitbox moveTo(Location newOrigin) {
        this.origin = newOrigin.clone();
        return this;
    }

    public Hitbox reorient(Vector direction) {
        if (shape instanceof Directional dir) {
            dir.setDirection(direction);
        }
        return this;
    }

    /**
     * Sets how far a swept shape reaches along its direction. Paired with {@link #moveTo} and
     * {@link #reorient}, this lets one hitbox follow a path and cover exactly the distance
     * travelled each tick instead of a length fixed when it was built — a sweep sized once at
     * spawn under-covers as the mover accelerates and leaves gaps in its own path. Reusing a
     * single hitbox this way is also what preserves its hit history, which is what stops a
     * swept path from re-hitting the same victim on every tick of the sweep.
     */
    public Hitbox resize(double length) {
        if (!(shape instanceof Swept swept)) {
            throw new UnsupportedOperationException(
                    shape.getClass().getSimpleName() + " has no swept length to resize");
        }
        swept.setLength(length);
        return this;
    }

    public Hitbox trackHits() {
        this.trackingHits = true;
        return this;
    }

    public Hitbox trackHits(long hitDelayMs) {
        this.trackingHits = true;
        this.hitDelay = hitDelayMs;
        return this;
    }

    public Hitbox allowRehit(UUID entityId) {
        hitHistory.remove(entityId);
        return this;
    }

    public Hitbox clearHitHistory() {
        hitHistory.clear();
        return this;
    }

    public Hitbox copyHitHistory(Hitbox other) {
        this.hitHistory.putAll(other.hitHistory);
        return this;
    }

    // --- Execution ---

    public Set<LivingEntity> get() {
        Set<LivingEntity> hits = new LinkedHashSet<>();
        Map<LivingEntity, Location> hitLocations = new HashMap<>();
        World world = origin.getWorld();
        if (world == null) return hits;

        // Wall state is per-check: without this reset, a reused moving hitbox (moveTo each
        // tick) reports a wall hit forever after its first one.
        wallHitLocation = null;

        // Expire old entries if re-hit is enabled
        if (trackingHits && hitDelay >= 0) {
            long now = System.currentTimeMillis();
            hitHistory.entrySet().removeIf(entry -> now >= entry.getValue() + hitDelay);
        }

        double maxDist = shape.getRoughRadius();
        
        if (checkWalls) {
            if (shape instanceof Directional dir) {
                RayTraceResult wallTrace = world.rayTraceBlocks(origin, dir.getDirection(), maxDist, FluidCollisionMode.NEVER, true);
                if (wallTrace != null) {
                    BlockFace face = wallTrace.getHitBlockFace();
                    Vector normal = new Vector(face.getModX(), face.getModY(), face.getModZ());
                    wallHitLocation = wallTrace.getHitPosition().add(normal.multiply(WALL_HIT_BACKOFF)).toLocation(world);
                    maxDist = origin.distance(wallHitLocation);
                }
            } else if (shape instanceof Sphere && (onHitWall != null || onExpire != null)) {
                // A sphere has no single direction to ray-trace along, so wall detection scans
                // the sphere for solid blocks. The nearest one (if any) becomes the wall-hit
                // location, letting abilities react via onHitWall / onExpire. That scan is
                // hundreds of block lookups per call, so it is only paid for when a callback
                // consumes it — for every other sphere, checkWalls means the per-victim
                // line-of-sight gate below and nothing more.
                wallHitLocation = WallDetection.nearestSolidBlockInSphere(origin, maxDist);
            }
        }

        BoundingBox searchArea = shape.getSearchBox(origin, maxDist);
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(searchArea)) {
            if (e instanceof LivingEntity le) {
                boolean matches = true;
                for (Predicate<LivingEntity> p : filters) {
                    if (!p.test(le)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) candidates.add(le);
            }
        }
        
        // 3. Precise Collision Check
        if (!pierce) {
            candidates.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)));
        }

        for (LivingEntity entity : candidates) {
            if (trackingHits && hitHistory.containsKey(entity.getUniqueId())) continue;
            if (!isAlive(entity)) continue;
            Location hitPos = shape.getIntersectionPoint(origin, entity.getBoundingBox(), maxDist);
            if (hitPos == null) continue;
            // Spheres aren't directional, so the ray-trace above can't gate hits by line of
            // sight. Gate each target individually instead: skip it when a solid block sits
            // between the sphere center and the point the sphere reported as hit.
            if (checkWalls && shape instanceof Sphere && isLineOfSightBlocked(world, origin, hitPos, maxDist)) continue;
            hits.add(entity);
            hitLocations.put(entity, hitPos);
            if (!pierce) break;
        }

        if (onHit != null) {
            hits.forEach(onHit);
        }
        
        if (onHitWithLocation != null) {
            hits.forEach(e -> onHitWithLocation.accept(e, hitLocations.get(e)));
        }

        if (wallHitLocation != null && onHitWall != null) {
            if (hits.isEmpty() || pierce) {
                onHitWall.accept(wallHitLocation);
            }
        }

        if (hits.isEmpty() && wallHitLocation == null && onExpire != null) {
            Location expireLoc = origin.clone();
            if (shape instanceof Directional dir) {
                expireLoc.add(dir.getDirection().clone().multiply(maxDist));
            }
            onExpire.accept(expireLoc);
        }

        if (trackingHits && !hits.isEmpty()) {
            long now = System.currentTimeMillis();
            for (LivingEntity e : hits) {
                hitHistory.put(e.getUniqueId(), now);
            }
        }

        return hits;
    }

    public Hitbox check() {
        get();
        return this;
    }

    public Location getWallHitLocation() { return wallHitLocation; }

    /**
     * True if a solid block lies between {@code origin} and {@code hitPos} — the point on the target
     * that the shape reported as struck, not the target's center. Aiming at the same point the hit
     * test used is what keeps the gate honest: the sphere accepts on the closest point of the AABB,
     * so tracing to the center overshot by up to ~1.0 block (half a player's AABB diagonal) and let
     * geometry the blast could never have touched veto a hit it had already authorized. The
     * {@code maxDistance} clamp holds that invariant if the two ever drift apart again.
     */
    private static boolean isLineOfSightBlocked(World world, Location origin, Location hitPos, double maxDistance) {
        // Line of sight from inside a solid block is undefined, so don't gate in that case.
        Block originBlock = origin.getBlock();
        if (originBlock.isSolid() && !originBlock.isPassable()) return false;

        Vector toTarget = hitPos.toVector().subtract(origin.toVector());
        double distance = Math.min(toTarget.length(), maxDistance);
        if (distance < 0.001) return false;

        RayTraceResult trace = world.rayTraceBlocks(origin, toTarget.normalize(), distance, FluidCollisionMode.NEVER, true);
        return trace != null;
    }

    // --- Shapes ---

    interface Shape {
        Location getIntersectionPoint(Location origin, BoundingBox box, double maxDist);
        BoundingBox getSearchBox(Location origin, double maxDist);
        double getRoughRadius();
    }

    interface Directional {
        Vector getDirection();
        void setDirection(Vector direction);
    }

    /** A shape with a swept length along its direction, resizable per tick via {@link #resize}. */
    interface Swept {
        void setLength(double length);
    }

    static class Sphere implements Shape {
        private final double radius;
        Sphere(double radius) { this.radius = radius; }

        @Override public Location getIntersectionPoint(Location origin, BoundingBox box, double maxDist) {
            Vector closest = new Vector(
                Math.max(box.getMinX(), Math.min(origin.getX(), box.getMaxX())),
                Math.max(box.getMinY(), Math.min(origin.getY(), box.getMaxY())),
                Math.max(box.getMinZ(), Math.min(origin.getZ(), box.getMaxZ()))
            );
            // The closest point is what the radius test accepts, so it — not the AABB center — is
            // where the sphere actually touched the target. Reporting it keeps every consumer
            // (notably the line-of-sight gate) pointed at the geometry the hit was granted on.
            if (origin.toVector().distance(closest) <= radius) {
                return closest.toLocation(origin.getWorld());
            }
            return null;
        }

        @Override public BoundingBox getSearchBox(Location origin, double maxDist) {
            return BoundingBox.of(origin, radius, radius, radius).expand(1.0);
        }

        @Override public double getRoughRadius() { return radius; }
    }

    static class Cylinder implements Shape, Directional, Swept {
        private Vector axis;
        private final double radius;
        private double height;
        Cylinder(Vector axis, double radius, double height) {
            this.axis = axis.clone().normalize();
            this.radius = radius;
            this.height = height;
        }
        @Override public Vector getDirection() { return axis; }
        @Override public void setDirection(Vector direction) { this.axis = direction.clone().normalize(); }
        @Override public void setLength(double length) { this.height = length; }
        @Override public Location getIntersectionPoint(Location origin, BoundingBox box, double maxDist) {
            RayTraceResult result = box.clone().expand(radius).rayTrace(origin.toVector(), axis, maxDist);
            return result != null ? result.getHitPosition().toLocation(origin.getWorld()) : null;
        }
        @Override public BoundingBox getSearchBox(Location origin, double maxDist) {
            Vector end = origin.toVector().add(axis.clone().multiply(maxDist));
            return BoundingBox.of(origin.toVector(), end).expand(radius + 1.0);
        }
        @Override public double getRoughRadius() { return height; }
    }

    static class Box implements Shape, Directional, Swept {
        private Vector direction;
        private final double halfWidth, halfHeight;
        private double length;
        Box(Vector direction, double width, double height, double length) {
            this.direction = direction.clone().normalize();
            this.halfWidth = width / 2;
            this.halfHeight = height / 2;
            this.length = length;
        }
        @Override public Vector getDirection() { return direction; }
        @Override public void setDirection(Vector direction) { this.direction = direction.clone().normalize(); }
        @Override public void setLength(double length) { this.length = length; }
        @Override public Location getIntersectionPoint(Location origin, BoundingBox box, double maxDist) {
            // Minkowski expansion: growing the target AABB by the box's half-extents and
            // ray-tracing the center line is exact for the world-axis-aligned cross-section
            // (the same technique vanilla uses for projectile-vs-entity checks).
            RayTraceResult result = box.clone().expand(halfWidth, halfHeight, halfWidth)
                    .rayTrace(origin.toVector(), direction, maxDist);
            return result != null ? result.getHitPosition().toLocation(origin.getWorld()) : null;
        }
        @Override public BoundingBox getSearchBox(Location origin, double maxDist) {
            Vector end = origin.toVector().add(direction.clone().multiply(maxDist));
            return BoundingBox.of(origin.toVector(), end).expand(Math.max(halfWidth, halfHeight) + 1.0);
        }
        @Override public double getRoughRadius() { return length; }
    }

    static class Cone implements Shape, Directional {
        private Vector direction;
        private final double minRadius, radius, height;
        Cone(Vector direction, double minRadius, double radius, double height) {
            this.direction = direction.clone().normalize();
            this.minRadius = minRadius;
            this.radius = radius;
            this.height = height;
        }
        @Override public Vector getDirection() { return direction; }
        @Override public void setDirection(Vector direction) { this.direction = direction.clone().normalize(); }
        @Override public Location getIntersectionPoint(Location origin, BoundingBox box, double maxDist) {
            Vector toPoint = box.getCenter().subtract(origin.toVector());
            double distAlongAxis = toPoint.dot(direction);
            if (distAlongAxis < 0 || distAlongAxis > maxDist) return null;
            double distFromAxis = toPoint.crossProduct(direction).length();
            // Frustum taper: minRadius wide at the origin, full radius at the far end.
            double maxRadiusAtDist = minRadius + (distAlongAxis / height) * (radius - minRadius);
            return distFromAxis <= (maxRadiusAtDist + 0.5) ? box.getCenter().toLocation(origin.getWorld()) : null;
        }
        @Override public BoundingBox getSearchBox(Location origin, double maxDist) {
            Vector end = origin.toVector().add(direction.clone().multiply(maxDist));
            return BoundingBox.of(origin.toVector(), end).expand(radius + 1.0);
        }
        @Override public double getRoughRadius() { return height; }
    }
}