package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Tracks a planted world object or entity-attached display with per-tick relative-offset sync. */
public class Planted {
    private final Location location;
    private final @Nullable LivingEntity attached;
    private final @Nullable Vector offset;
    private @Nullable ItemDisplay display;

    public Planted(Location location, @Nullable LivingEntity attached, @Nullable ItemDisplay display) {
        this.location = location.clone();
        this.attached = attached;
        this.offset = attached != null ? location.toVector().subtract(attached.getLocation().toVector()) : null;
        this.display = display;
    }

    public void sync() {
        if (attached != null && attached.isValid()) {
            Location base = attached.getLocation();
            location.setX(base.getX() + offset.getX());
            location.setY(base.getY() + offset.getY());
            location.setZ(base.getZ() + offset.getZ());
            if (display != null && display.isValid()) {
                display.teleport(AbstractDisplayBuilder.canonical(location));
            }
        }
    }

    public Location location() { return location; }

    public boolean isAttached() { return attached != null; }

    public void cleanup() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        display = null;
    }
}
