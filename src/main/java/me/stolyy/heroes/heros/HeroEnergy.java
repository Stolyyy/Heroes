package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class HeroEnergy extends HeroCooldown {
    private double energy;
    private double maxEnergy;
    private double energyPerTick;
    private boolean canIncreaseEnergy;

    public HeroEnergy(Player player){
        super(player);
    }

    protected void setEnergyStats(double energy, double maxEnergy, double energyPerTick, boolean canIncreaseEnergy){
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
                if(canIncreaseEnergy){
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

    protected HeroEnergy setEnergy(double energy) {
        this.energy = Math.min(Math.max(energy, 0), maxEnergy);
        return this;
    }
    protected HeroEnergy addEnergy(double energy) {
        this.energy = Math.min(Math.max(this.energy + energy, 0), maxEnergy);
        return this;
    }
    protected HeroEnergy subtractEnergy(double energy) {
        this.energy = Math.min(Math.max(this.energy - energy, 0), maxEnergy);
        return this;
    }
    protected HeroEnergy setMaxEnergy(double maxEnergy) {
        this.maxEnergy = maxEnergy;
        return this;
    }
    protected HeroEnergy setEnergyPerTick(double energyPerTick) {
        this.energyPerTick = energyPerTick;
        return this;
    }
    protected HeroEnergy setCanIncreaseEnergy(boolean canIncreaseEnergy) {
        this.canIncreaseEnergy = canIncreaseEnergy;
        return this;
    }

    protected double energy() {
        return energy;
    }
    protected double maxEnergy() {
        return maxEnergy;
    }
    protected double energyPerTick() {
        return energyPerTick;
    }
    protected boolean canIncreaseEnergy() {
        return canIncreaseEnergy;
    }
}

