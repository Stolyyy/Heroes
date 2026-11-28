package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.game.PracticeArena;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
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

        Location minionLoc = hero.getController().getLocation();
        Location ownerLoc = owner.getController().getLocation();
        PracticeArena arena = PracticeManager.practiceArenaOf(owner);
        if (arena != null && !arena.contains(minionLoc)) {
            hero.getController().teleport(ownerLoc);
            return;
        }

        double dist = minionLoc.distance(ownerLoc);
        if (dist > tetherDistance) {
            hero.getController().teleport(ownerLoc);
        }
    }
}
