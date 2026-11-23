package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class ShoopPrimary extends Projectile {
    public ShoopPrimary(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }
}
