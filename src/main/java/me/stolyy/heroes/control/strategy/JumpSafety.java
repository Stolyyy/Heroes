package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Prevention half of the bot off-map work: veto strategy jumps whose projected landing is
 * over the void or whose upward arc would slam into a ceiling. A vetoed chase jump is a
 * PRESERVED double jump — recovery fuel the bot would otherwise have burned.
 *
 * <p>Reach projection is deliberately rough: {@code xStrength × airtime}, airtime from the
 * jump's launch velocity against the standard 0.08/tick gravity. It only has to be right
 * enough to separate "lands on stage" from "lands in the abyss".
 */
public final class JumpSafety {
    private static final double GRAVITY_PER_TICK = 0.08;
    private static final Vector UP = new Vector(0, 1, 0);

    private JumpSafety() {
    }

    /**
     * A jump toward {@code target} is safe when its landing point has ground below it, OR
     * the target itself is within reach (+ the configured margin) — jumping ACROSS a gap
     * onto the target's platform is a legitimate play, sailing halfway into it is not.
     */
    public static boolean isChaseJumpSafe(Hero hero, BotController controller, LivingEntity target) {
        if (!BotConfig.isJumpVetoEnabled()) return true;
        Location botLoc = controller.getLocation();
        Vector toTarget = target.getLocation().toVector().subtract(botLoc.toVector()).setY(0);
        if (toTarget.lengthSquared() < 0.01) return true; // on top of each other: any hop is fine

        double reach = jumpReach(hero);
        if (!landsOverVoid(botLoc, toTarget.normalize(), reach)) return true;
        return botLoc.distance(target.getLocation()) <= reach + BotConfig.preventionReachMargin();
    }

    /**
     * True when the space straight above the bot's eyes is clear up to its jump apex
     * ({@code yStrength² / (2 × 0.08)}, capped at {@code prevention.headroom_max_check}).
     */
    public static boolean hasHeadroom(Hero hero, BotController controller) {
        if (!BotConfig.isJumpVetoEnabled()) return true;
        double yStrength = hero.getMobility().getYStrength();
        double apex = (yStrength * yStrength) / (2.0 * GRAVITY_PER_TICK);
        double checkHeight = Math.min(apex, BotConfig.preventionHeadroomMaxCheck());
        if (checkHeight <= 0) return true;

        Location eye = controller.getEyeLocation();
        return eye.getWorld().rayTraceBlocks(eye, UP, checkHeight, FluidCollisionMode.NEVER, true) == null;
    }

    private static double jumpReach(Hero hero) {
        double airTicks = Math.max(1, 2 * hero.getMobility().getYStrength() / GRAVITY_PER_TICK);
        return hero.getMobility().getXStrength() * airTicks;
    }

    private static boolean landsOverVoid(Location from, Vector dir, double reach) {
        Location landing = from.clone().add(dir.clone().multiply(reach));
        return WallDetection.isAboveVoid(landing);
    }
}
