package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class Tether extends BaseAbility {
    private final double tetherDistance;

    public Tether(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.tetherDistance = config.getDouble("tether_distance", 15.0);
    }

    @Override
    public void perform(Hero hero) {
        // Does nothing on execute - tether is tick-based
    }

    @Override
    protected void onTick(Hero hero) {
        if (!(hero instanceof MinionHero minion)) return;
        Hero owner = minion.getOwner();
        if (owner == null || owner.getController() == null) return;

        double dist = hero.getController().getLocation()
                .distance(owner.getController().getLocation());
        if (dist > tetherDistance) {
            hero.getController().getEntity()
                    .teleport(owner.getController().getLocation());
        }
    }
}
