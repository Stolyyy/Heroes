package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class ShoopSecondary extends Hitscan {
    private final double[] damageValues;
    private final double[] kbValues;
    private final VisualEffect[] trailValues;
    private final SoundEffect[] soundValues;

    public ShoopSecondary(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.damageValues = this.config.getDoubleList("charge_damage").stream().mapToDouble(Double::doubleValue).toArray();
        this.kbValues = this.config.getDoubleList("charge_knockback").stream().mapToDouble(Double::doubleValue).toArray();
        if (this.damageValues.length == 0 || this.kbValues.length == 0) {
            throw new IllegalStateException("ShoopSecondary requires non-empty charge_damage and charge_knockback lists");
        }

        ConfigurationSection chargeLevels = Configs.requireSection(this.config, "charge_levels");
        var keys = chargeLevels.getKeys(false).stream().sorted().toList();
        this.soundValues = new SoundEffect[keys.size()];
        this.trailValues = new VisualEffect[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            ConfigurationSection level = Configs.requireSection(chargeLevels, keys.get(i));
            this.soundValues[i] = new SoundEffect(Configs.requireSection(level, "fire_sound"));
            this.trailValues[i] = new VisualEffect(Configs.requireSection(level, "trail_visual"));
        }
    }

    @Override
    protected ShotParams shotParamsFor(Hero hero) {
        int index = Math.max(0, getAmmo().getLastConsumed() - 1);
        return new ShotParams(damageValues[index], kbValues[index], 0.0, soundValues[index], trailValues[index], hitVisual);
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if ((ability.getType() == AbilityType.PRIMARY || ability.getType() == AbilityType.PASSIVE_SNEAK) && Hero.grantsWholeGain(victim)) {
            getAmmo().addAmmo(1);
            cooldown.charge(1);
        }
    }
}
