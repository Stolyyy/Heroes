package me.stolyy.heroes.ability.kit.pug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class PugPounce extends BaseAbility {
    private boolean isCharging = false;
    private final float chargingRegen;
    private final double damagePerEnergy;
    private final double baseStrength;
    private final double strengthPerEnergy;
    private final long buffDuration;
    private long buffEndTime;
    private float energyAtStart;

    protected SoundEffect fireSound;
    protected SoundEffect activationSound;

    public PugPounce(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.chargingRegen = (float) Configs.requireDouble(this.config, "charging_regen");
        this.damagePerEnergy = Configs.requireDouble(this.config, "damage_per_energy");
        this.baseStrength = Configs.requireDouble(this.config, "base_strength");
        this.strengthPerEnergy = Configs.requireDouble(this.config, "strength_per_energy");
        this.buffDuration = Configs.requireLong(this.config, "buff_duration_ms");

        this.fireSound = new SoundEffect(Configs.requireSection(this.config, "fire_sound"));
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "activation_sound"));
    }

    @Override
    protected boolean canUse(Hero hero) {
        return !isCharging;
    }

    @Override
    public void perform(Hero hero) {
        energyAtStart = hero.getEnergy().getCurrentEnergy();
        isCharging = true;
        hero.getEnergy().setRegenOverride(this, chargingRegen);
        activationSound.play(hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        if (isCharging) {
            boolean sneaking = hero.getController().isSneaking();
            float currentEnergy = hero.getEnergy().getCurrentEnergy();

            if (!sneaking || currentEnergy <= 0.1) {
                pounce(hero);
            }
        }

        if (buffEndTime > 0 && System.currentTimeMillis() > buffEndTime) {
            resetBuff(hero);
        }
    }

    private void pounce(Hero hero) {
        isCharging = false;
        hero.getEnergy().removeRegenOverride(this);

        float energyUsed = Math.max(0, energyAtStart - hero.getEnergy().getCurrentEnergy());

        Vector dir = hero.getController().getLocation().getDirection();
        // BaseAbility already consumed energy_cost before perform(), so energyUsed measures only
        // energy burned while holding sneak; an uncharged tap gives base_strength and zero bonus damage.
        double vel = baseStrength + (energyUsed * strengthPerEnergy);
        hero.getController().setVelocity(dir.multiply(vel));

        double bonusDamage = energyUsed * damagePerEnergy;
        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().addDamageFlat(this, bonusDamage);
        }

        buffEndTime = System.currentTimeMillis() + buffDuration;

        fireSound.play(hero.getController());
    }

    private void resetBuff(Hero hero) {
        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().removeDamageModifier(this);
        }
        buffEndTime = 0;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        clearState(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        clearState(hero);
    }

    private void clearState(Hero hero) {
        if (isCharging) {
            hero.getEnergy().removeRegenOverride(this);
            isCharging = false;
        }
        if (buffEndTime > 0) {
            resetBuff(hero);
        }
    }
}
