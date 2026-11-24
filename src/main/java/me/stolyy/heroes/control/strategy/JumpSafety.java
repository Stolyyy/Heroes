package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Prevention half of the bot off-map work: veto strategy jumps whose projected landing is
 * over the void. A vetoed chase jump is a PRESERVED double jump — recovery fuel the bot
 * would otherwise have burned hurling itself after a target it can't reach.
 *
 * <p>Reach projection is deliberately rough: {@code xStrength × airtime}, airtime from the
 * jump's launch velocity against the standard 0.08/tick gravity. It only has to be right
 * enough to separate "lands on stage" from "lands in the abyss".
 */
public final class JumpSafety {
    private static final double GRAVITY_PER_TICK = 0.08;

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

    private static double jumpReach(Hero hero) {
        double airTicks = Math.max(1, 2 * hero.getMobility().getYStrength() / GRAVITY_PER_TICK);
        return hero.getMobility().getXStrength() * airTicks;
    }

    private static boolean landsOverVoid(Location from, Vector dir, double reach) {
        Location landing = from.clone().add(dir.clone().multiply(reach));
        return WallDetection.isAboveVoid(landing);
    }
}
