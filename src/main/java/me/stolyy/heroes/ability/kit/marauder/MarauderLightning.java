package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class MarauderLightning extends Hitscan {
    private final int energyPerJab;

    public MarauderLightning(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.energyPerJab = Configs.requireInt(this.config, "energy_per_jab");
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        hero.getEnergy().changeEnergy(energyPerJab);
    }
}
