package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Landing-safety veto for movement abilities that launch the caster off its current position —
 * currently just {@link Dash}. Every other ability passes unconditionally: nothing else in the
 * ability system moves the caster across a gap the way a dash does, so there is nothing to
 * project a landing for (this deliberately does NOT special-case arcing/gravity abilities —
 * a dash lands where {@code speed x duration} says it lands, full stop).
 *
 * <p>Recovery allowance (default ON): a dash that would land over the void is still permitted
 * when the bot has at least one double jump left — "dash off then jump back" is a legitimate
 * recovery play, not a suicide. It is vetoed only when the bot has nothing left to recover
 * with. Modeled on {@link JumpSafety}, whose reach projection makes the same trade for jumps.
 */
public final class DashSafety {
    private DashSafety() {
    }

    /**
     * @param direction the direction the ability will launch along — the caster's facing at
     *                  fire time for a live check, or the intended aim direction for a
     *                  look-before-you-leap check made just before {@code lookAt} is issued.
     */
    public static boolean isLandingSafe(Hero hero, BotController controller, Ability ability, Vector direction) {
        if (!(ability instanceof Dash dash)) return true;
        if (direction.lengthSquared() < 0.01) return true;

        double projectedDistance = dash.getSpeed() * dash.getDurationTicks();
        Location landing = controller.getLocation().clone()
                .add(direction.clone().normalize().multiply(projectedDistance));
        if (!WallDetection.isAboveVoid(landing)) return true;

        return hero.getMobility().getRemainingJumps() >= 1;
    }
}
