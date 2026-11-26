package me.stolyy.heroes.ability.kit.cryomancer;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class CryomancerTimeWarp extends BaseAbility {
    private Location warpLoc;
    private final float energyCostPerSecond;

    private final VisualEffect anchorVisual;
    private final VisualEffect auraVisual;
    private final int auraInterval;
    private int auraTicks;
    private final SoundEffect setSound, recallSound;

    public CryomancerTimeWarp(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.energyCostPerSecond = (float) Configs.requireDouble(this.config, "energy_cost_per_second");
        this.anchorVisual = new VisualEffect(Configs.requireSection(this.config, "anchor_visual"));
        this.auraVisual = new VisualEffect(Configs.requireSection(this.config, "aura_visual"));
        this.auraInterval = Configs.requireInt(this.config, "aura_interval");
        this.setSound = new SoundEffect(Configs.requireSection(this.config, "set_sound"));
        this.recallSound = new SoundEffect(Configs.requireSection(this.config, "recall_sound"));
    }

    @Override
    protected boolean canRecast(Hero hero) {
        return warpLoc != null;
    }

    @Override
    protected boolean tryRecast(Hero hero) {
        if (warpLoc == null) return false;
        recall(hero);
        return true;
    }

    @Override
    public void perform(Hero hero) {
        warpLoc = hero.getController().getLocation();
        setSound.play(hero.getController().getLocation());
        hero.getEnergy().setRegenOverride(this, energyCostPerSecond / -20);
    }

    private void recall(Hero hero) {
        WallDetection.safeTeleport(hero.getController(), warpLoc);
        recallSound.play(hero.getController().getLocation());
        warpLoc = null;
        hero.getEnergy().removeRegenOverride(this);
    }

    @Override
    protected void onTick(Hero hero) {
        if (warpLoc == null) return;

        if (hero.getEnergy().getCurrentEnergy() < 1) {
            warpLoc = null;
            hero.getEnergy().removeRegenOverride(this);
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
        clearWarp(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        clearWarp(hero);
    }

    private void clearWarp(Hero hero) {
        if (warpLoc != null) {
            hero.getEnergy().removeRegenOverride(this);
            warpLoc = null;
        }
    }
}