package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.hero.minion.MinionHero;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/**
 * Base AI for minion bots. Extends {@link BaseBotAI} with shared idle behaviour:
 * when no enemy target is found the minion pathfinds back toward its owner.
 *
 * Subclasses may still override {@link #onIdle} if they need extra logic before
 * or after the owner-follow navigation (e.g. clearing a sneaking flag).
 */
public abstract class MinionBotAI extends BaseBotAI {

    public MinionBotAI(ConfigurationSection config, Strategy strategy) {
        super(config, strategy);
    }

    /** Minions never enter RECOVER — Tether already teleports strays back to their owner. */
    @Override
    protected boolean recoveryEnabled() {
        return false;
    }

    /** Minions never EVADE — a princess panic-fleeing reads as broken, and her spacing is
     *  already the ring seeking. */
    @Override
    protected boolean evadeEnabled() {
        return false;
    }

    /** Minions can't pick crystals up (pickup eligibility is activePlayers). */
    @Override
    protected boolean seeksCrystals() {
        return false;
    }

    /**
     * Default idle behaviour: follow the owner who summoned this minion.
     * Subclasses that override this should call {@code super.onIdle(hero, controller)}
     * to preserve the follow logic.
     */
    @Override
    protected void onIdle(Hero hero, BotController controller) {
        if (hero instanceof MinionHero minion && minion.getOwner() != null
                && minion.getOwner().getController() != null) {
            LivingEntity ownerEntity = minion.getOwner().getController().getEntity();
            if (ownerEntity != null) {
                // Guarded re-issue (see BaseBotAI.navigateTo): Citizens tracks the moving owner,
                // so issuing once and only re-issuing on change avoids per-tick path resets.
                navigateTo(controller, ownerEntity);
                return;
            }
        }
        // No owner to follow (e.g. owner mid-respawn) — stop rather than hold a stale path.
        controller.stopNavigation();
        lastNavTarget = null;
    }
}
