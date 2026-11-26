package me.stolyy.heroes.ability.kit.cakemonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.configuration.ConfigurationSection;

public class Regurgicake extends Projectile {
    private final SoundEffect activationSound;

    public Regurgicake(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero){
        super.perform(hero);
        activationSound.play(hero.getController());
    }
}
