package me.stolyy.heroes.vfx.effect;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Pooled line of persistent {@link ItemDisplay} segments resized dynamically by distance. */
public final class BeamDisplay {
    private final VisualEffect segmentVisual;
    private final double spacing;
    private final List<ItemDisplay> segments = new ArrayList<>();
    private int visibleSegments = 0;
    private float segmentViewRange = 1.0f;

    public BeamDisplay(VisualEffect segmentVisual, double spacing) {
        if (!Double.isFinite(spacing) || spacing <= 0) {
            throw new IllegalArgumentException("BeamDisplay spacing must be > 0, got " + spacing);
        }
        this.segmentVisual = segmentVisual;
        this.spacing = spacing;
    }

    /** Repositions, grows, or toggles segment visibility to span {@code distance} from {@code start}. */
    public void update(Location start, Vector direction, double distance, String skinId) {
        Vector dir = direction.clone().normalize();
        double backOffset = segmentVisual.modelBackOffset();
        int needed = (int) Math.floor(distance / spacing) + 1;
        int total = Math.max(needed, segments.size());

        for (int i = 0; i < total; i++) {
            Location loc = start.clone().add(dir.clone().multiply(i * spacing));
            if (i < segments.size()) {
                ItemDisplay segment = segments.get(i);
                segment.setInterpolationDelay(0);
                AbstractDisplayBuilder.follow(segment, loc, dir, backOffset);
            } else {
                ItemDisplay segment = segmentVisual.spawnDisplay(loc, skinId);
                if (segment == null) {
                    throw new IllegalStateException("BeamDisplay requires an ITEM_DISPLAY segment visual");
                }
                segment.setTeleportDuration(1);
                segment.setInterpolationDuration(1);
                AbstractDisplayBuilder.orient(segment, dir, backOffset);
                segmentViewRange = segment.getViewRange();
                segments.add(segment);
            }
        }

        for (int i = Math.min(needed, visibleSegments); i < Math.max(needed, visibleSegments); i++) {
            segments.get(i).setViewRange(i < needed ? segmentViewRange : 0f);
        }
        visibleSegments = needed;
    }

    public void clear() {
        for (ItemDisplay segment : segments) {
            segment.remove();
        }
        segments.clear();
        visibleSegments = 0;
    }
}
