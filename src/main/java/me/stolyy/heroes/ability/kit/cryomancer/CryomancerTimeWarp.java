package me.stolyy.heroes.ability.kit.cryomancer;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class CryomancerTimeWarp extends BaseAbility {
    private Location warpLoc;
    private float normalEnergyRegen;
    private final float energyCostPerSecond;

    private final VisualEffect anchorVisual;
    private final VisualEffect auraVisual;
    private final int auraInterval;
    private int auraTicks;
    private final SoundEffect setSound, recallSound;

    public CryomancerTimeWarp(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.energyCostPerSecond = (float) config.getDouble("energy_cost_per_second", 20);
        this.anchorVisual = new VisualEffect(config.getConfigurationSection("anchor_visual"));
        this.auraVisual = new VisualEffect(config.getConfigurationSection("aura_visual"));
        this.auraInterval = config.getInt("aura_interval", 5);
        this.setSound = new SoundEffect(config.getConfigurationSection("set_sound"));
        this.recallSound = new SoundEffect(config.getConfigurationSection("recall_sound"));
    }

    @Override
    public boolean execute(Hero hero) {
        if (warpLoc == null) {
            if (!hero.getEnergy().tryConsume(hero.getController(), getEnergyCost())) return false;
            setWarp(hero);
        } else {
            recall(hero);
        }
        return true;
    }

    private void setWarp(Hero hero) {
        warpLoc = hero.getController().getLocation();

        setSound.play(hero.getController().getLocation());

        normalEnergyRegen = hero.getEnergy().getRegenPerTick();
        hero.getEnergy().setRegenPerTick(energyCostPerSecond / -20);
    }

    private void recall(Hero hero){
        hero.getController().getEntity().teleport(warpLoc);
        recallSound.play(hero.getController().getLocation());
        warpLoc = null;
        hero.getEnergy().setRegenPerTick(normalEnergyRegen);
    }

    @Override
    public void perform(Hero hero) {
    }

    @Override
    protected void onTick(Hero hero) {
        if (warpLoc == null) return;

        if (hero.getEnergy().getCurrentEnergy() < 1) {
            warpLoc = null;
            hero.getEnergy().setRegenPerTick(normalEnergyRegen);
            return;
        }

        anchorVisual.play(warpLoc);
        if (++auraTicks >= auraInterval) {
            auraTicks = 0;
            auraVisual.play(hero.getController().getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        if (warpLoc != null) {
            hero.getEnergy().setRegenPerTick(normalEnergyRegen);
            warpLoc = null;
        }
    }
}