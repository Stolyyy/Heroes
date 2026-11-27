package me.stolyy.heroes.ability.kit.skullfire;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class SkullfireDeagle extends Hitscan {
    private final int shotsWhileSneaking;

    public SkullfireDeagle(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        shotsWhileSneaking = Configs.requireInt(this.config, "shots_while_sneaking");
    }

    @Override
    protected int burstCountFor(Hero hero) {
        return hero.getController().isSneaking() ? shotsWhileSneaking : 1;
    }

    @Override
    protected long activeDurationFor(Hero hero) {
        return hero.getController().isSneaking() ? (long) burstDelay * (shotsWhileSneaking - 1) : 0;
    }

    @Override
    protected void onImpact(Hero hero, Location location, @Nullable LivingEntity victim, ImpactCause cause) {
        if (cause == ImpactCause.HIT && victim != null) {
            hero.findAbility(SkullfireBlazingDeagle.class).ifPresent(ult -> ult.onPrimaryHitResolved(hero, victim));
        }
    }
}
