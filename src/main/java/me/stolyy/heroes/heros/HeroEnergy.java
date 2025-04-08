package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class HeroEnergy extends HeroCooldown {
    protected double energy;
    protected double maxEnergy;
    protected double energyPerTick;
    protected boolean canIncreaseEnergy;

    public HeroEnergy(Player player){
        super(player);
    }

    protected void setEnergyStats(double Energy, double maxEnergy, double energyPerTick, boolean canIncreaseEnergy){
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.energyPerTick = energyPerTick;
        this.canIncreaseEnergy = canIncreaseEnergy;
    }

    protected void initializeEnergyUpdates() {
        HeroEnergy heroEnergy = this;
        new BukkitRunnable(){
            @Override
            public void run() {
                if(!HeroManager.getHero(player).equals(heroEnergy)) {
                    this.cancel();
                    return;
                }
                if(energy < maxEnergy && canIncreaseEnergy){
                    energy = Math.min(maxEnergy, Math.max(energy + energyPerTick, 0));
                }
                updateXpBar();
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 1L);
    }

    protected void updateXpBar() {
        int xpLevels = Math.max( (int) energy, 0);
        float xpProgress = (float) (Math.max(energy, 0) / maxEnergy);
        xpProgress = Math.max(0.0f, Math.min(1.0f, xpProgress));
        player.setLevel(xpLevels);
        player.setExp(xpProgress);
    }
}

