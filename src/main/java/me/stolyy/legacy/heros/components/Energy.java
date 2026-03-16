package me.stolyy.heroes.heros.components;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.configs.EnergyConfig;

public class Energy implements OnTick {
    private final Hero hero;

    private final double maxEnergy;
    private double currentEnergy, regenRate; // regen rate in energy per second
    private boolean canChangeEnergy;

    public Energy(Hero hero, EnergyConfig config) {
        this.hero = hero;

        maxEnergy = config.maxEnergy();
        regenRate = config.energyRegen();
        currentEnergy = 0;
        canChangeEnergy = true;
    }

    public void onTick(){
        if(canChangeEnergy) {
            changeEnergy(regenRate / 20);
        }
        hero.getDisplay().setXPBar(currentEnergy, currentEnergy / maxEnergy);
    }

    public void clean() { }

    public void changeEnergy(double change) {
        currentEnergy = Math.min(Math.max(currentEnergy + change, 0), maxEnergy);
    }

    public void allowEnergyChanges(){
        canChangeEnergy = true;
    }

    public void disallowEnergyChanges(){
        canChangeEnergy = false;
    }

    public void setEnergyPerSecond(double regenRate){
        this.regenRate = regenRate;
    }

    public double getCurrentEnergy(){
        return currentEnergy;
    }

    public double getMaxEnergy(){
        return maxEnergy;
    }
}
