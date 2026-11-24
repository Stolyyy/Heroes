package me.stolyy.heroes.ability.kit.tinman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Shockwave;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class Overload extends Shockwave {
    private final double launchX;
    private final double launchY;

    public Overload(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.launchX = Configs.requireDouble(this.config, "launch_x");
        this.launchY = Configs.requireDouble(this.config, "launch_y");
    }

    @Override
    public void perform(Hero hero) {
        super.perform(hero);

        Vector velocity = hero.getController().getLocation().getDirection().multiply(launchX);
        velocity.add(new Vector(0, launchY, 0));
        hero.getController().setVelocity(velocity);
    }
}