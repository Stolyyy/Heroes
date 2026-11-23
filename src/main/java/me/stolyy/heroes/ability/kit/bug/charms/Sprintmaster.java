package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.configuration.ConfigurationSection;

public class Sprintmaster extends Charm {
    private final double speedMult;
    private final double stepHeightAdd;

    public Sprintmaster(ConfigurationSection config) {
        this.speedMult = config != null ? config.getDouble("speed_mult", 1.2) : 1.2;
        this.stepHeightAdd = config != null ? config.getDouble("step_height_add", 0.4) : 0.4;
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getAttributes().setMovementSpeed(hero.getController(),
                hero.getAttributes().getMovementSpeed() * speedMult);
        hero.getAttributes().setStepHeight(hero.getController(),
                hero.getAttributes().getStepHeight() + stepHeightAdd);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().setMovementSpeed(hero.getController(),
                hero.getAttributes().getMovementSpeed() / speedMult);
        hero.getAttributes().setStepHeight(hero.getController(),
                hero.getAttributes().getStepHeight() - stepHeightAdd);
    }
}
