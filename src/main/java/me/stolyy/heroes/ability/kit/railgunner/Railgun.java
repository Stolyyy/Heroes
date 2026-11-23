package me.stolyy.heroes.ability.kit.railgunner;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class Railgun extends Projectile {
    private final double headshotTolerance;
    private final SoundEffect headshotSound;

    private boolean currentShotHeadshot;

    public Railgun(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.headshotTolerance = Configs.requireDouble(this.config, "headshot_tolerance");

        this.headshotSound = new SoundEffect(Configs.requireSection(this.config, "visuals.headshot_sound"));
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        boolean isHeadshot = Math.abs(victim.getEyeLocation().getY() - hitLoc.getY()) < headshotTolerance;
        currentShotHeadshot = isHeadshot;

        for (Ability passive : attacker.getPassiveAbilities()) {
            if (passive instanceof HeadHunter) {
                ((HeadHunter) passive).setHeadshot(isHeadshot);
                break;
            }
        }

        super.onHit(attacker, victim, hitLoc);
    }

    @Override
    protected SoundEffect hitSoundFor(Hero attacker, LivingEntity victim, Location hitLoc) {
        return currentShotHeadshot ? headshotSound : hitSound;
    }
}
