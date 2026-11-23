package me.stolyy.heroes.ability.kit.pug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class PugPounce extends BaseAbility {
    private double basePrimaryDamage;
    private boolean isCharging = false;
    private float originalRegen;
    private final float chargingRegen;
    private final double damagePerEnergy;
    private final double baseStrength;
    private final double strengthPerEnergy;
    private final long buffDuration;
    private long buffEndTime;
    private float energyAtStart;
    private final float upfrontCost;

    protected SoundEffect fireSound;
    protected SoundEffect activationSound;

    public PugPounce(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.chargingRegen = (float) config.getDouble("charging_regen", -2.0);
        this.damagePerEnergy = config.getDouble("damage_per_energy", 0.1);
        this.baseStrength = Configs.requireDouble(config, "base_strength");
        this.strengthPerEnergy = Configs.requireDouble(config, "strength_per_energy");
        this.buffDuration = config.getLong("buff_duration_ms", 3000);
        this.upfrontCost = (float) config.getDouble("energy_cost", 30.0);

        this.fireSound = new SoundEffect(config.getConfigurationSection("fire_sound"));
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void initialize(Hero hero) {
        if (hero.getPrimaryAbility() != null) {
            this.basePrimaryDamage = hero.getPrimaryAbility().getDamage();
        }
        this.originalRegen = hero.getEnergy().getRegenPerTick();
    }

    @Override
    public boolean execute(Hero hero) {
        if (isCharging || !canExecute(hero)) return false;
        
        perform(hero);
        return true;
    }

    @Override
    public void perform(Hero hero) {
        energyAtStart = hero.getEnergy().getCurrentEnergy();
        hero.getEnergy().tryConsume(hero.getController(), upfrontCost);
        isCharging = true;
        hero.getEnergy().setRegenPerTick(chargingRegen);
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
        hero.getEnergy().setRegenPerTick(originalRegen);

        float energyUsed = energyAtStart - hero.getEnergy().getCurrentEnergy();
        if (energyUsed < 0) energyUsed = 0;

        Vector dir = hero.getController().getLocation().getDirection();
        // energyUsed is measured from before the upfront cost is spent, so an instantly-released
        // pounce already reads as upfrontCost of drain. Subtracting it makes strength_per_energy
        // and damage_per_energy measure only the energy burned while holding sneak — an uncharged
        // tap gives exactly base_strength and zero bonus damage.
        double vel = baseStrength + ((energyUsed - upfrontCost) * strengthPerEnergy);
        hero.getController().setVelocity(dir.multiply(vel));

        double bonusDamage = Math.max(0, energyUsed - upfrontCost) * damagePerEnergy;
        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().setDamage(basePrimaryDamage + bonusDamage);
        }
        
        buffEndTime = System.currentTimeMillis() + buffDuration;
        
        fireSound.play(hero.getController());
    }

    private void resetBuff(Hero hero) {
        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().setDamage(basePrimaryDamage);
        }
        buffEndTime = 0;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        if (isCharging) {
            hero.getEnergy().setRegenPerTick(originalRegen);
            isCharging = false;
        }
        if (buffEndTime > 0) {
            resetBuff(hero);
        }
    }
}
