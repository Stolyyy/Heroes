package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class MarauderUltimate extends BaseAbility {
    private boolean isActive = false;

    private final double damageMitigation;
    private final float energyRegenMultiplier;
    private float originalEnergyRegen;
    private final SoundEffect activationSound;

    private final VisualEffect auraVisual;
    private final int auraInterval;
    private int auraTicks;

    public MarauderUltimate(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.damageMitigation = config.getDouble("damage_mitigation");
        this.energyRegenMultiplier = (float) config.getDouble("energy_regen_multiplier");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.auraVisual = new VisualEffect(this.config.getConfigurationSection("visuals.aura_visual"));
        this.auraInterval = this.config.getInt("visuals.aura_interval", 10);
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;

        hero.getAttributes().setDamageMultiplier(damageMitigation);

        activationSound.play(hero.getController());
        
        originalEnergyRegen = hero.getEnergy().getRegenPerTick();
        hero.getEnergy().setRegenPerTick(originalEnergyRegen * energyRegenMultiplier);
    }

    @Override
    protected void onTick(Hero hero) {
        if (!isActive) return;

        if (!getCooldownObject().isInUse()) {
            onExpire(hero, null);
            return;
        }

        if (++auraTicks >= auraInterval) {
            auraTicks = 0;
            auraVisual.play(hero.getController().getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        if (!isActive) return;
        isActive = false;
        
        hero.getAttributes().resetDamageMultiplier();
        hero.getEnergy().setRegenPerTick(originalEnergyRegen);
    }
}