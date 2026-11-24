package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/** Close-range melee/rush strategy; never enters {@code EVADE} panic. */
public class Aggressive extends ConfigStrategy {

    public Aggressive(ConfigurationSection config) {
        super(config, "aggressive", false);
    }

    @Override
    public boolean performMovement(Hero hero, BotController controller, LivingEntity target) {
        if (!canDoubleJump()) return false;

        Location botLoc = controller.getLocation();
        Location targetLoc = target.getLocation();

        if (targetLoc.getY() - botLoc.getY() > BotConfig.chaseJumpHeightThreshold()) {
            return tryChaseJump(hero, controller, target);
        }

        // Check horizontal distance so being airborne directly above the target does not re-trigger jumps.
        double horizontal = Strategy.horizontalDistance(botLoc, targetLoc);
        if (horizontal > Math.max(getFarDistance(), getPreferredDistance() + BotConfig.ringSlack())) {
            return tryChaseJump(hero, controller, target);
        }

        return false;
    }
}
