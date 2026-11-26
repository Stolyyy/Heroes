package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.minion.MinionHero;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/**
 * Minion bot AI configured via {@code bot.strategy_type}: skips recovery, evade panic, and
 * crystals, and follows its summoning owner when no enemy target is present.
 */
public class MinionBotAI extends HeroBotAI {

    public MinionBotAI(ConfigurationSection config) {
        super(config, Strategy.fromConfig(config));
    }

    @Override
    protected boolean recoveryEnabled() {
        return false;
    }

    @Override
    protected boolean evadeEnabled() {
        return false;
    }

    @Override
    protected boolean seeksCrystals() {
        return false;
    }

    @Override
    protected void onIdle(Hero hero, BotController controller) {
        if (hero instanceof MinionHero minion && minion.getOwner() != null
                && !minion.getOwner().isRespawning() && !minion.getOwner().isEliminated()) {
            Controller ownerController = minion.getOwner().getController();
            if (ownerController == null) {
                throw new IllegalStateException("Minion owner " + minion.getOwner().getName() + " has no controller");
            }
            LivingEntity ownerEntity = ownerController.getEntity();
            if (ownerEntity != null) {
                navigateToEntity(controller, ownerEntity);
                return;
            }
        }
        stopNavigation(controller);
    }
}
