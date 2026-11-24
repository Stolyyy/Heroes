package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;

/** Wraps a steerable or ballistic {@link ItemDisplay} vehicle mounted by a {@link Hero}. */
public final class RideDisplay {
    private ItemDisplay display;

    private RideDisplay(ItemDisplay display) {
        this.display = display;
    }

    /** Configures smooth interpolation and mounts {@code rider} onto {@code display}. */
    public static RideDisplay mount(Hero rider, ItemDisplay display) {
        display.setTeleportDuration(1);
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        rider.getController().mount(display);
        return new RideDisplay(display);
    }

    public boolean isValid() {
        return display != null && display.isValid() && !display.isDead();
    }

    public boolean isRiderGone(Hero rider) {
        return rider.getController() == null || rider.getController().getEntity() == null
                || rider.getController().getEntity().isDead() || rider.isRespawning();
    }

    public Location location() {
        return display.getLocation();
    }

    public void moveTo(Location loc) {
        display.teleport(AbstractDisplayBuilder.canonical(loc));
    }

    public void moveTo(Location loc, Vector heading) {
        moveTo(loc);
        display.setInterpolationDelay(0);
        AbstractDisplayBuilder.orient(display, heading);
    }

    public void orient(Vector heading) {
        AbstractDisplayBuilder.orient(display, heading);
    }

    public void dismountAndRemove(Hero rider) {
        if (rider.getController() != null) {
            rider.getController().leaveVehicle();
        }
        if (display != null) {
            display.eject();
            display.remove();
            display = null;
        }
    }
}
