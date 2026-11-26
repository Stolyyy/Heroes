package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Minion passive template that teleports a minion back near its owner when separation exceeds
 * {@code tether_distance}.
 */
public class Tether extends BaseAbility {
    private final double tetherDistance;

    public Tether(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.tetherDistance = Configs.orTemplate(this.config, "tether_distance", "tether.tether_distance");
    }

    @Override
    public void perform(Hero hero) {
        // Does nothing on execute - tether is tick-based
    }

    @Override
    protected void onTick(Hero hero) {
        if (!(hero instanceof MinionHero minion)) {
            throw new IllegalStateException("Tether ability '" + getName() + "' can only be attached to a MinionHero, got " + hero.getName());
        }
        Hero owner = minion.getOwner();
        if (owner == null || owner.getController() == null) return;
        // Skip tethering while the owner is respawning or eliminated so minions keep chasing
        // instead of teleporting to (and giving away) the owner's respawn point.
        if (owner.isRespawning() || owner.isEliminated()) return;

        double dist = hero.getController().getLocation()
                .distance(owner.getController().getLocation());
        if (dist > tetherDistance) {
            hero.getController().teleport(owner.getController().getLocation());
        }
    }
}
