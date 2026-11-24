package me.stolyy.heroes.ability.kit.bulk;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.DebrisBurst;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class BulkCar extends Projectile {
    private final DebrisBurst debris;

    public BulkCar(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.debris = new DebrisBurst(Configs.requireSection(this.config, "visuals.explosion_debris"));
    }

    @Override
    protected void onProjectileTick(Hero hero) {
        debris.tick();
    }

    @Override
    protected void onBeforeImpact(Hero hero, Location location, @Nullable LivingEntity victim, ImpactCause cause) {
        debris.spawn(location);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        debris.clear();
    }
}
