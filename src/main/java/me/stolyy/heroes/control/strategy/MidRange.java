package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/** Hybrid spacing strategy that chase-jumps only outside the hero's spacing ring. */
public class MidRange extends ConfigStrategy {

    public MidRange(ConfigurationSection config) {
        super(config, "midrange", true);
    }

    @Override
    public boolean performMovement(Hero hero, BotController controller, LivingEntity target) {
        if (!canDoubleJump()) return false;

        Location botLoc = controller.getLocation();
        Location targetLoc = target.getLocation();
        double horizontal = Strategy.horizontalDistance(botLoc, targetLoc);
        if (horizontal <= getPreferredDistance() + BotConfig.ringSlack()) {
            return false;
        }

        if (targetLoc.getY() - botLoc.getY() > BotConfig.chaseJumpHeightThreshold()) {
            return tryChaseJump(hero, controller, target);
        }
        if (horizontal > getFarDistance()) {
            return tryChaseJump(hero, controller, target);
        }

        return false;
    }
}
