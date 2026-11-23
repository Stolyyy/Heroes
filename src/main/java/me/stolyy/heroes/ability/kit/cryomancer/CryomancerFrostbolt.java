package me.stolyy.heroes.ability.kit.cryomancer;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CryomancerFrostbolt extends Projectile {
    private final int slownessDuration;

    public CryomancerFrostbolt(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        slownessDuration = config.getInt("slowness_duration");
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        super.onHit(attacker, victim, hitLoc);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessDuration, 0, false, false, false));
    }
}
