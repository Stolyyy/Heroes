package me.stolyy.heroes.ability.kit.karakot;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class KiBlast extends Projectile {
    public KiBlast(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    protected long burstDelayMsFor(Hero hero) {
        return cooldown.getCooldownDuration() / 2;
    }
}
