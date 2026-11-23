package me.stolyy.heroes.ability.kit.skullfire;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class SkullfireDeagle extends Hitscan {
    private final int shotsWhileSneaking;

    public SkullfireDeagle(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        shotsWhileSneaking = config.getInt("shots_while_sneaking");
        burstDelay = (int) (config.getDouble("sneak_shot_delay") * 1000);
    }

    @Override
    public boolean execute(Hero hero) {
        activeDuration = hero.getController().isSneaking()
                ? (long) burstDelay * (shotsWhileSneaking - 1) : 0;
        return super.execute(hero);
    }

    @Override
    public void perform(Hero hero){
        burstCount = hero.getController().isSneaking() ? shotsWhileSneaking : 1;
        super.perform(hero);
    }
}
