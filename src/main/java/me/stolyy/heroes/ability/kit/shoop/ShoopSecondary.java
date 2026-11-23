package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.Hitscan;
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

        this.damageValues = config.getDoubleList("charge_damage").stream().mapToDouble(Double::doubleValue).toArray();
        this.kbValues = config.getDoubleList("charge_knockback").stream().mapToDouble(Double::doubleValue).toArray();

        ConfigurationSection chargeLevels = config.getConfigurationSection("charge_levels");
        if (chargeLevels != null) {
            var keys = chargeLevels.getKeys(false).stream().sorted().toList();
            this.soundValues = new SoundEffect[keys.size()];
            this.trailValues = new VisualEffect[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                ConfigurationSection level = chargeLevels.getConfigurationSection(keys.get(i));
                this.soundValues[i] = new SoundEffect(level.getConfigurationSection("fire_sound"));
                this.trailValues[i] = new VisualEffect(level.getConfigurationSection("trail_visual"));
            }
        } else {
            this.soundValues = new SoundEffect[0];
            this.trailValues = new VisualEffect[0];
        }
    }

    @Override
    public void perform(Hero hero) {
        int used = getAmmo().getLastConsumed();
        applyStats(used);
        super.perform(hero);
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if ((ability.getType() == AbilityType.PRIMARY || ability.getType() == AbilityType.PASSIVE_SNEAK) && Hero.grantsWholeGain(victim)) {
            getAmmo().addAmmo(1);
            cooldown.charge(1);
        }
    }

    private void applyStats(int chargeLevel) {
        int index = Math.max(0, chargeLevel - 1);
        this.damage = damageValues[index];
        this.knockback = kbValues[index];
        this.trailVisual = trailValues[index];
        this.fireSound = soundValues[index];
    }
}