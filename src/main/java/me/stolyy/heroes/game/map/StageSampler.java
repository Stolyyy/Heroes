package me.stolyy.heroes.game.map;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-map "where is the stage" answer for bot recovery. On first query for a world, a
 * coarse horizontal grid across the map bounds is raycast straight down; every solid hit
 * becomes a stage point (multi-level maps contribute a point per top surface). One-time
 * cost of ~a thousand block lookups per game world; the cache is keyed weakly by World so
 * entries die with their (per-instance, uniquely named) game worlds.
 */
public final class StageSampler {
    private static final Map<World, List<Vector>> CACHE = new WeakHashMap<>();
    private static final double GRID_STEP = 3.0;
    // Vertical distance is penalized when the point is ABOVE the seeker — a falling bot
    // should prefer the ledge beside it over a platform overhead it cannot reach.
    private static final double ABOVE_WEIGHT = 2.0;

    private StageSampler() {
    }

    /**
     * The stage point nearest to {@code from} by the weighted metric, or null when the map
     * sampled empty (no solid ground inside the bounds — a broken map, not a normal state).
     */
    public static Location nearestStagePoint(GameMap map, Location from) {
        List<Vector> points = CACHE.computeIfAbsent(map.world(), w -> sample(map));
        if (points.isEmpty()) return null;

        Vector origin = from.toVector();
        Vector best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vector point : points) {
            double dx = point.getX() - origin.getX();
            double dz = point.getZ() - origin.getZ();
            double dy = point.getY() - origin.getY();
            double weightedDy = dy > 0 ? dy * ABOVE_WEIGHT : dy;
            double score = dx * dx + dz * dz + weightedDy * weightedDy;
            if (score < bestScore) {
                bestScore = score;
                best = point;
            }
        }
        return best == null ? null : best.toLocation(map.world());
    }

    private static List<Vector> sample(GameMap map) {
        List<Vector> points = new ArrayList<>();
        World world = map.world();
        var bounds = map.boundaries();
        double top = bounds.getMaxY() - 0.5;
        double minY = bounds.getMinY() + 0.5;
        for (double x = bounds.getMinX() + 1; x <= bounds.getMaxX() - 1; x += GRID_STEP) {
            for (double z = bounds.getMinZ() + 1; z <= bounds.getMaxZ() - 1; z += GRID_STEP) {
                sampleColumn(world, x, z, top, minY, points);
            }
        }
        return points;
    }

    private static final Vector DOWN = new Vector(0, -1, 0);

    // One column: raycast down for the first STANDABLE surface. Barrier blocks are the maps'
    // invisible roofs/walls — a bot can neither reach nor stand on them, and anchoring on the
    // roof sent recovering bots pumping jumps at the ceiling. Step past every barrier hit and
    // keep looking down; a column that is barrier-all-the-way-down contributes no point.
    private static void sampleColumn(World world, double x, double z, double top, double minY,
                                     List<Vector> points) {
        double y = top;
        while (y > minY) {
            RayTraceResult hit = world.rayTraceBlocks(new Location(world, x, y, z),
                    DOWN, y - minY, FluidCollisionMode.NEVER, true);
            if (hit == null) return;
            if (hit.getHitBlock() != null && hit.getHitBlock().getType() == Material.BARRIER) {
                y = hit.getHitPosition().getY() - 1.0; // skip the barrier block, continue below
                continue;
            }
            points.add(hit.getHitPosition());
            return;
        }
    }
}
